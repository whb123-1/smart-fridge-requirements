package com.xianzhi.fridge.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xianzhi.fridge.inventory.infrastructure.FoodCatalog;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalogAlias;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalogAliasRepository;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalogRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FoodNormalizationServiceTest {
    private final FoodCatalogRepository catalogs = mock(FoodCatalogRepository.class);
    private final FoodCatalogAliasRepository aliases = mock(FoodCatalogAliasRepository.class);
    private final FoodNormalizationService service = new FoodNormalizationService(catalogs, aliases);

    @Test
    void resolvesCanonicalNameBeforeAliases() {
        FoodCatalog tomato = mock(FoodCatalog.class);
        when(catalogs.findByCanonicalNameIgnoreCase("番茄")).thenReturn(Optional.of(tomato));
        assertThat(service.resolve("  番茄  ")).isSameAs(tomato);
    }

    @Test
    void resolvesApprovedAliasWithWhitespaceAndCaseNormalization() {
        UUID catalogId = UUID.randomUUID();
        FoodCatalog tomato = mock(FoodCatalog.class);
        FoodCatalogAlias alias = mock(FoodCatalogAlias.class);
        when(alias.getCatalogId()).thenReturn(catalogId);
        when(catalogs.findByCanonicalNameIgnoreCase("ＴＯＭＡＴＯ")).thenReturn(Optional.empty());
        when(aliases.findFirstByNormalizedAliasAndApprovedTrue("tomato")).thenReturn(Optional.of(alias));
        when(catalogs.findById(catalogId)).thenReturn(Optional.of(tomato));
        assertThat(service.resolve(" ＴＯＭＡＴＯ ")).isSameAs(tomato);
    }

    @Test
    void resolvesCanonicalNameWithInternalWhitespaceAndWidthVariation() {
        FoodCatalog tomato = mock(FoodCatalog.class);
        when(tomato.getCanonicalName()).thenReturn("Tomato");
        when(catalogs.findByCanonicalNameIgnoreCase("ＴＯＭＡ ＴＯ")).thenReturn(Optional.empty());
        when(catalogs.findAll()).thenReturn(List.of(tomato));
        assertThat(service.resolve(" ＴＯＭＡ ＴＯ ")).isSameAs(tomato);
    }

    @Test
    void doesNotMergeUnapprovedOrUnknownAlias() {
        when(catalogs.findByCanonicalNameIgnoreCase("西红柿候选")).thenReturn(Optional.empty());
        when(aliases.findFirstByNormalizedAliasAndApprovedTrue("西红柿候选")).thenReturn(Optional.empty());
        assertThat(service.resolve("西红柿候选")).isNull();
    }
}
