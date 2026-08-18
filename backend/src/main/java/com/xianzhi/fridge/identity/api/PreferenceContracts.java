package com.xianzhi.fridge.identity.api;

import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PreferenceContracts {
    private PreferenceContracts() { }
    public record UpdateRequest(List<@Size(max=64) String> tastes,List<@Size(max=64) String> cuisines,
                                List<@Size(max=120) String> allergies,List<@Size(max=120) String> dislikes,
                                @Size(max=32) String dietaryGoal,@Min(500) @Max(10000) Integer calorieTarget,
                                TemperatureUnit temperatureUnit) { }
    public record View(List<String> tastes,List<String> cuisines,List<String> allergies,List<String> dislikes,
                       String dietaryGoal,Integer calorieTarget,TemperatureUnit temperatureUnit) { }
}
