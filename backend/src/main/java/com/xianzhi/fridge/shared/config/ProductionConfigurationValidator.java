package com.xianzhi.fridge.shared.config;

import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.speech.config.SpeechProperties;
import com.xianzhi.fridge.speech.config.StorageProperties;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionConfigurationValidator implements ApplicationRunner {
    private final AppProperties app;
    private final TelemetryProperties telemetry;
    private final SpeechProperties speech;
    private final StorageProperties storage;
    private final AssistantProperties ai;
    private final Environment environment;

    public ProductionConfigurationValidator(AppProperties app, TelemetryProperties telemetry,
            SpeechProperties speech, StorageProperties storage, AssistantProperties ai, Environment environment) {
        this.app=app;this.telemetry=telemetry;this.speech=speech;this.storage=storage;this.ai=ai;this.environment=environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<String> errors=new ArrayList<>();
        List<String> profiles=Arrays.stream(environment.getActiveProfiles()).map(value->value.toLowerCase(Locale.ROOT)).toList();
        String databasePassword=environment.getProperty("spring.datasource.password");
        requireSecret(databasePassword,16,"database password",errors);
        if(profiles.contains("migration")){
            if(!errors.isEmpty())throw new IllegalStateException("Unsafe migration configuration: "+String.join("; ",errors));
            return;
        }
        if(profiles.contains("dev")||profiles.contains("simulator"))errors.add("prod cannot run with dev or simulator profile");
        requireHttps(app.getPublicUrl(),"APP_PUBLIC_URL",errors);
        if(!app.getSecurity().isRefreshCookieSecure())errors.add("REFRESH_COOKIE_SECURE must be true");
        requireSecret(app.getSecurity().getJwtSigningKey(),64,"JWT_SIGNING_KEY",errors);
        requireSecret(app.getIdentity().getTombstoneKey(),32,"IDENTITY_TOMBSTONE_KEY",errors);
        if(equal(app.getSecurity().getJwtSigningKey(),app.getIdentity().getTombstoneKey()))errors.add("JWT and tombstone keys must be different");
        if(!app.getSecurity().getAdminUsernames().isEmpty())errors.add("ADMIN_USERNAMES must be empty in prod");
        if(!telemetry.debugOperators().isEmpty())errors.add("DEBUG_TEST_OPERATOR_USERNAMES must be empty in prod");
        if(telemetry.isEnabled()){
            requireSecret(telemetry.getServicePassword(),20,"MQTT_SERVICE_PASSWORD",errors);
            requireSecret(telemetry.getInternalToken(),32,"MQTT_INTERNAL_TOKEN",errors);
            String publicBroker=telemetry.getPublicBrokerUrl();
            if(publicBroker==null||!publicBroker.startsWith("wss://"))errors.add("MQTT_PUBLIC_BROKER_URL must use wss://");
        }
        if(speech.isFakeEnabled()||"fake".equalsIgnoreCase(speech.getProvider()))errors.add("fake speech provider is forbidden in prod");
        if("openai".equalsIgnoreCase(speech.getProvider())){
            requireHttp(speech.getBaseUrl(),"SPEECH_BASE_URL",errors);
            if(!"s3".equalsIgnoreCase(storage.getProvider()))errors.add("OpenAI speech requires S3 object storage in prod");
        }
        if("s3".equalsIgnoreCase(storage.getProvider())){
            requireSecret(storage.getAccessKey(),8,"STORAGE_ACCESS_KEY",errors);
            requireSecret(storage.getSecretKey(),20,"STORAGE_SECRET_KEY",errors);
            if(!storage.isServerSideEncryption())errors.add("S3 server-side encryption must be enabled");
        } else if(!"disabled".equalsIgnoreCase(speech.getProvider()))errors.add("local object storage is forbidden for enabled speech in prod");
        if(ai.isExternalCallsEnabled())requireHttp(ai.getBaseUrl(),"AI_BASE_URL",errors);
        if(ai.isVectorEnabled()){
            if(!"openai".equalsIgnoreCase(ai.getEmbeddingProvider()))errors.add("production vector search requires real OpenAI-compatible embeddings");
            requireHttp(ai.getEmbeddingBaseUrl(),"EMBEDDING_BASE_URL",errors);
            requireHttp(ai.getQdrantUrl(),"QDRANT_URL",errors);
            if(ai.getEmbeddingDimensions()<64)errors.add("EMBEDDING_DIMENSIONS is invalid");
        }
        if(!errors.isEmpty())throw new IllegalStateException("Unsafe production configuration: "+String.join("; ",errors));
    }

    private static void requireHttps(String value,String name,List<String> errors){try{URI uri=URI.create(value==null?"":value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)errors.add(name+" must be an absolute https:// URL");}catch(RuntimeException exception){errors.add(name+" must be an absolute https:// URL");}}
    private static void requireHttp(String value,String name,List<String> errors){try{URI uri=URI.create(value==null?"":value);if(uri.getHost()==null||!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme())))errors.add(name+" must be an absolute HTTP(S) URL");}catch(RuntimeException exception){errors.add(name+" must be an absolute HTTP(S) URL");}}
    private static void requireSecret(String value,int minimum,String name,List<String> errors){String normalized=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(value==null||value.length()<minimum||normalized.contains("change-me")||normalized.contains("replace-with")||normalized.contains("development"))errors.add(name+" is missing, weak, or still uses a default value");}
    private static boolean equal(String left,String right){return left!=null&&left.equals(right);}
}
