package com.xianzhi.fridge.recipe.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TavilyWebRecipeSearchAdapter implements WebRecipeSearchPort {
    private static final int MAX_SUMMARY_CHARS = 4000;
    private final AssistantProperties properties;
    private final ExternalProviderClient client;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TavilyWebRecipeSearchAdapter(AssistantProperties properties, ExternalProviderClient client, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public boolean enabled() {
        return properties.isExternalCallsEnabled() && text(properties.getTavilyApiKey()) && text(properties.getTavilyBaseUrl());
    }

    @Override
    public SearchResult search(String query) {
        if (!enabled()) return new SearchResult(List.of(), List.of("联网搜索未启用，请配置 TAVILY_API_KEY"), true);
        if (query == null || query.isBlank()) return new SearchResult(List.of(), List.of("请输入要搜索的菜名或核心食材"), false);
        try {
            var body = mapper.createObjectNode();
            body.put("api_key", properties.getTavilyApiKey());
            body.put("query", query.trim() + " 菜谱 主料 做法");
            body.put("search_depth", "advanced");
            body.put("topic", "general");
            body.put("include_answer", false);
            body.put("include_raw_content", false);
            body.put("max_results", Math.max(1, Math.min(10, properties.getTavilyMaxResults())));
            JsonNode output = client.postJsonLimited("tavily", endpoint(properties.getTavilyBaseUrl(), "/search"), null, body, properties.getTavilyTimeout(), 1_000_000);
            List<Source> sources = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Instant retrievedAt = clock.instant();
            for (JsonNode result : output.path("results")) {
                if (sources.size() >= properties.getTavilyMaxResults()) break;
                String url = result.path("url").asText("").trim();
                if (!publicHttpUrl(url)) {
                    warnings.add("已忽略不安全的来源地址");
                    continue;
                }
                String summary = limited(result.path("content").asText(""), MAX_SUMMARY_CHARS);
                String title = limited(result.path("title").asText("未命名来源"), 300);
                if (summary.isBlank()) continue;
                URI uri = URI.create(url);
                sources.add(new Source(title, summary, url, uri.getHost(), retrievedAt, "tavily-v1"));
            }
            if (sources.isEmpty()) warnings.add("联网搜索没有返回可验证的公开菜谱来源");
            return new SearchResult(sources, warnings.stream().distinct().toList(), sources.isEmpty());
        } catch (Exception exception) {
            return new SearchResult(List.of(), List.of("联网搜索暂时不可用，本次未生成菜谱草稿"), true);
        }
    }

    static boolean publicHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) return false;
            String host = uri.getHost().toLowerCase();
            if (host.equals("localhost") || host.endsWith(".local") || host.equals("metadata.google.internal") || host.equals("169.254.169.254")) return false;
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static String limited(String value, int max) { return value == null ? "" : value.trim().substring(0, Math.min(value.trim().length(), max)); }
    private static String endpoint(String base, String path) { String value = base.replaceAll("/$", ""); return value.endsWith(path) ? value : value + path; }
}
