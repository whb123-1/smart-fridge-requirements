package com.xianzhi.fridge.identity.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.identity.api.PreferenceContracts;
import com.xianzhi.fridge.identity.infrastructure.*;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceService {
    private final UserPreferenceRepository preferences;private final AppUserRepository users;private final IdempotencyService idempotency;private final ObjectMapper mapper;private final Clock clock;
    public UserPreferenceService(UserPreferenceRepository preferences,AppUserRepository users,IdempotencyService idempotency,ObjectMapper mapper,Clock clock){this.preferences=preferences;this.users=users;this.idempotency=idempotency;this.mapper=mapper;this.clock=clock;}
    @Transactional(readOnly=true) public PreferenceContracts.View get(UUID userId){AppUser user=users.findById(userId).orElseThrow();UserPreference value=preferences.findById(userId).orElse(null);return value==null?new PreferenceContracts.View(List.of(),List.of(),List.of(),List.of(),null,null,user.getTemperatureUnit()):view(value,user);}
    @Transactional public PreferenceContracts.View update(UUID userId,String key,PreferenceContracts.UpdateRequest request){String path="/api/v1/me/preferences";PreferenceContracts.View replay=idempotency.replay(userId,key,"PUT",path,request,PreferenceContracts.View.class);if(replay!=null)return replay;AppUser user=users.findById(userId).orElseThrow();UserPreference value=preferences.findById(userId).orElseGet(()->new UserPreference(userId,clock.instant()));value.update(json(request.tastes()),json(request.cuisines()),json(request.allergies()),json(request.dislikes()),request.dietaryGoal(),request.calorieTarget(),clock.instant());preferences.save(value);if(request.temperatureUnit()!=null)user.updateProfile(user.getUsername(),user.getDisplayName(),user.getTimezone(),request.temperatureUnit());PreferenceContracts.View result=view(value,user);idempotency.save(userId,key,"PUT",path,request,result,200);return result;}
    private PreferenceContracts.View view(UserPreference value,AppUser user){return new PreferenceContracts.View(list(value.getTastes()),list(value.getCuisines()),list(value.getAllergies()),list(value.getDislikes()),value.getDietaryGoal(),value.getCalorieTarget(),user.getTemperatureUnit());}
    private String json(List<String> values){try{return mapper.writeValueAsString(values==null?List.of():values.stream().map(String::trim).filter(v->!v.isBlank()).distinct().toList());}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private List<String> list(String json){try{return mapper.readValue(json,new TypeReference<>(){});}catch(JsonProcessingException e){return List.of();}}
}
