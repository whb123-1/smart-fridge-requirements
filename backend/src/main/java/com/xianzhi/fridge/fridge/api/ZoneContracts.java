package com.xianzhi.fridge.fridge.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class ZoneContracts {
    private ZoneContracts() { }

    public record UpdateRequest(
            @NotBlank @Size(max = 48) String name,
            @NotNull @DecimalMin("-40.0") @DecimalMax("40.0") BigDecimal targetTemperatureC,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal targetHumidityPct) { }

    public record ZoneView(UUID id, String name, BigDecimal targetTemperatureC, BigDecimal targetHumidityPct) { }
}
