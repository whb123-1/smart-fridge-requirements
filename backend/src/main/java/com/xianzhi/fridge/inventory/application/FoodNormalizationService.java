package com.xianzhi.fridge.inventory.application;

import com.xianzhi.fridge.inventory.infrastructure.FoodCatalog;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalogAliasRepository;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalogRepository;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class FoodNormalizationService {
    private final FoodCatalogRepository catalogs;
    private final FoodCatalogAliasRepository aliases;

    public FoodNormalizationService(FoodCatalogRepository catalogs, FoodCatalogAliasRepository aliases) {
        this.catalogs = catalogs;
        this.aliases = aliases;
    }

    public String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public FoodCatalog resolve(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return null;
        FoodCatalog exact = catalogs.findByCanonicalNameIgnoreCase(value.trim()).orElse(null);
        if (exact != null) return exact;
        FoodCatalog normalizedCanonical = catalogs.findAll().stream()
                .filter(catalog -> normalize(catalog.getCanonicalName()).equals(normalized))
                .findFirst().orElse(null);
        if (normalizedCanonical != null) return normalizedCanonical;
        return aliases.findFirstByNormalizedAliasAndApprovedTrue(normalized)
                .flatMap(alias -> catalogs.findById(alias.getCatalogId())).orElse(null);
    }

    public boolean equivalent(String first, String second) {
        String normalizedFirst = normalize(first);
        String normalizedSecond = normalize(second);
        if (normalizedFirst.isBlank() || normalizedSecond.isBlank()) return false;
        if (normalizedFirst.equals(normalizedSecond)) return true;
        FoodCatalog firstCatalog = resolve(first);
        FoodCatalog secondCatalog = resolve(second);
        return firstCatalog != null && secondCatalog != null && firstCatalog.getId().equals(secondCatalog.getId());
    }
}
