package com.xianzhi.fridge.assistant.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xianzhi.fridge.identity.application.UserPreferenceService;
import com.xianzhi.fridge.inventory.application.InventoryService;
import com.xianzhi.fridge.shared.domain.Hashing;
import java.time.Clock;
import java.util.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AssistantContextAssembler {
    private final UserPreferenceService preferences;private final InventoryService inventory;private final ObjectMapper mapper;private final Clock clock;
    public AssistantContextAssembler(UserPreferenceService preferences,InventoryService inventory,ObjectMapper mapper,Clock clock){this.preferences=preferences;this.inventory=inventory;this.mapper=mapper;this.clock=clock;}
    public Snapshot assemble(UUID userId,String page,JsonNode selection){var pref=preferences.get(userId);var items=inventory.listItems(userId,null,null,null,null,null);List<Map<String,Object>> inventoryContext=items.stream().limit(50).map(item->{Map<String,Object> value=new LinkedHashMap<>();value.put("id",item.id());value.put("name",item.name());value.put("category",item.category());value.put("lowStock",item.lowStock());value.put("batches",item.batches().stream().map(batch->Map.of("id",batch.id(),"quantity",batch.remainingQuantity(),"unit",batch.unit(),"status",batch.status())).toList());return value;}).toList();Map<String,Object> data=new LinkedHashMap<>();data.put("preferences",Map.of("tastes",pref.tastes(),"cuisines",pref.cuisines(),"allergies",pref.allergies(),"dislikes",pref.dislikes(),"dietaryGoal",String.valueOf(pref.dietaryGoal())));data.put("inventory",inventoryContext);Map<String,Object> context=new LinkedHashMap<>(data);context.put("page",page);context.put("selection",sanitizeSelection(selection));context.put("assembledAt",clock.instant());try{String version=Hashing.sha256(mapper.writeValueAsString(data));String json=mapper.writeValueAsString(context);return new Snapshot(version,json,mapper.writeValueAsString(Map.of("inventory","current","preferences","current")),items);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    public JsonNode sanitizeSelection(JsonNode selection){ObjectNode safe=mapper.createObjectNode();if(selection==null||!selection.isObject())return safe;for(String field:List.of("listId","itemName","quantity","unit","recipeId","batchId","zoneId")){JsonNode value=selection.get(field);if(value==null||value.isContainerNode())continue;if(value.isTextual())safe.put(field,value.asText().substring(0,Math.min(160,value.asText().length())));else if(value.isNumber())safe.set(field,value);else if(value.isBoolean())safe.put(field,value.asBoolean());}return safe;}
    public record Snapshot(String version,String json,String sources,List<com.xianzhi.fridge.inventory.api.InventoryContracts.ItemView> inventory){}
}
