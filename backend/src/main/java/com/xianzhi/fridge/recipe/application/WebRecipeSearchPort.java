package com.xianzhi.fridge.recipe.application;

import java.time.Instant;
import java.util.List;

public interface WebRecipeSearchPort {
    SearchResult search(String query);
    boolean enabled();

    record Source(String title, String summary, String url, String site, Instant retrievedAt, String sourceVersion) { }
    record SearchResult(List<Source> sources, List<String> warnings, boolean fallback) { }
}
