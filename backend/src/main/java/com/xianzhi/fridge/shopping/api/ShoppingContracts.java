package com.xianzhi.fridge.shopping.api;

import com.xianzhi.fridge.shopping.domain.ShoppingStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShoppingContracts {
    private ShoppingContracts() { }
    public record CreateListRequest(UUID fridgeId, @NotBlank @Size(max = 120) String name) { }
    public record CreateItemRequest(@NotBlank @Size(max = 120) String name, @Size(max = 24) String category,
                                    @DecimalMin("0") BigDecimal quantity, @Size(max = 24) String unit,
                                    @Size(max = 255) String note, @Size(max = 32) String sourceType) { }
    public record UpdateItemRequest(@Size(max = 120) String name, @Size(max = 24) String category,
                                    @DecimalMin("0") BigDecimal quantity, @Size(max = 24) String unit,
                                    @Size(max = 255) String note, ShoppingStatus status) { }
    public record StoreRequest(UUID fridgeId, UUID zoneId, @DecimalMin("0") BigDecimal quantity,
                               @Size(max = 24) String unit, Instant storedAt, Integer shelfLifeDays) { }
    public record ItemView(UUID id, String name, String category, BigDecimal quantity, String unit,
                           String note, ShoppingStatus status, String sourceType) { }
    public record ListView(UUID id, UUID fridgeId, String name, List<ItemView> items) { }
}
