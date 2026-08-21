package com.xianzhi.fridge.shared.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ExternalProviderClient {
    private final HttpClient client=HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper;private final MeterRegistry meters;private final Map<String,Circuit> circuits=new ConcurrentHashMap<>();
    public ExternalProviderClient(ObjectMapper mapper,MeterRegistry meters){this.mapper=mapper;this.meters=meters;}
    public JsonNode postJson(String provider,String url,String apiKey,JsonNode body,Duration timeout){
        return postJsonLimited(provider,url,apiKey,body,timeout,2_000_000,false);
    }
    public JsonNode postJsonLimited(String provider,String url,String apiKey,JsonNode body,Duration timeout,int maxResponseBytes){
        return postJsonLimited(provider,url,apiKey,body,timeout,maxResponseBytes,true);
    }
    private JsonNode postJsonLimited(String provider,String url,String apiKey,JsonNode body,Duration timeout,int maxResponseBytes,boolean requireJson){
        return execute(provider,()->authorized(HttpRequest.newBuilder(validUri(url)).timeout(timeout)
                .header("Content-Type","application/json").header("Accept","application/json"),apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8)).build(),maxResponseBytes,requireJson);
    }
    public JsonNode postAudio(String provider,String url,String apiKey,InputStreamSupplier audio,String model,String language,Duration timeout){
        String boundary="----xianzhi"+Long.toHexString(System.nanoTime());
        return execute(provider,()->authorized(HttpRequest.newBuilder(validUri(url)).timeout(timeout)
                .header("Content-Type","multipart/form-data; boundary="+boundary).header("Accept","application/json"),apiKey)
                .POST(multipart(boundary,audio,model,language)).build(),2_000_000,false);
    }
    private JsonNode execute(String provider,RequestFactory factory,int maxResponseBytes,boolean requireJson){
        Circuit circuit=circuits.computeIfAbsent(provider,key->new Circuit());
        if(circuit.openUntil!=null&&Instant.now().isBefore(circuit.openUntil))throw new IllegalStateException(provider+" circuit is open");
        Timer.Sample sample=Timer.start(meters);RuntimeException failure=null;
        try{
            for(int attempt=0;attempt<3;attempt++){
                try{
                    HttpResponse<InputStream> response=client.send(factory.create(),HttpResponse.BodyHandlers.ofInputStream());
                    if(response.statusCode()>=200&&response.statusCode()<300){String contentType=response.headers().firstValue("Content-Type").orElse("").toLowerCase();if(requireJson&&!contentType.contains("application/json")){response.body().close();throw new IllegalStateException(provider+" returned a non-JSON response");}byte[] bytes=response.body().readNBytes(Math.max(1,maxResponseBytes)+1);response.body().close();if(bytes.length>maxResponseBytes)throw new IllegalStateException(provider+" response exceeded size limit");circuit.success();meters.counter("xianzhi.provider.calls","provider",provider,"outcome","success").increment();return mapper.readTree(bytes);}
                    response.body().close();
                    if(response.statusCode()!=429&&response.statusCode()<500)throw new IllegalStateException(provider+" returned HTTP "+response.statusCode());
                    failure=new IllegalStateException(provider+" returned HTTP "+response.statusCode());
                }catch(IOException exception){failure=new IllegalStateException(provider+" request failed",exception);}
                catch(InterruptedException exception){Thread.currentThread().interrupt();throw new IllegalStateException(provider+" request interrupted",exception);}
                if(attempt<2)try{Thread.sleep(250L*(attempt+1));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }
            throw failure==null?new IllegalStateException(provider+" request failed"):failure;
        }catch(RuntimeException exception){circuit.failure();meters.counter("xianzhi.provider.calls","provider",provider,"outcome","failure").increment();throw exception;}
        finally{sample.stop(meters.timer("xianzhi.provider.latency","provider",provider));}
    }
    private static URI validUri(String value){URI uri=URI.create(value);if(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))throw new IllegalArgumentException("Provider URL must use HTTP(S)");return uri;}
    private static HttpRequest.Builder authorized(HttpRequest.Builder builder,String key){return key==null||key.isBlank()?builder:builder.header("Authorization","Bearer "+key);}
    private static HttpRequest.BodyPublisher multipart(String boundary,InputStreamSupplier audio,String model,String language){StringBuilder prefix=new StringBuilder("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n").append(model).append("\r\n");if(language!=null&&!language.isBlank())prefix.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\n").append(language).append("\r\n");prefix.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.webm\"\r\nContent-Type: audio/webm\r\n\r\n");return HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(prefix.toString(),StandardCharsets.UTF_8),HttpRequest.BodyPublishers.ofInputStream(()->{try{return audio.open();}catch(IOException exception){throw new UncheckedIOException(exception);}}),HttpRequest.BodyPublishers.ofString("\r\n--"+boundary+"--\r\n",StandardCharsets.UTF_8));}
    @FunctionalInterface private interface RequestFactory{HttpRequest create()throws IOException;}
    @FunctionalInterface public interface InputStreamSupplier{InputStream open()throws IOException;}
    private static final class Circuit{private int failures;private Instant openUntil; synchronized void success(){failures=0;openUntil=null;}synchronized void failure(){if(++failures>=5){openUntil=Instant.now().plusSeconds(30);failures=0;}}}
}
