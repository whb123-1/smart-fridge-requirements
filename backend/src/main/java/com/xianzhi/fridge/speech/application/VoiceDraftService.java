package com.xianzhi.fridge.speech.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.inventory.api.InventoryContracts;
import com.xianzhi.fridge.inventory.application.InventoryService;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.OutboxEvent;
import com.xianzhi.fridge.shared.infrastructure.OutboxEventRepository;
import com.xianzhi.fridge.shared.web.ApiException;
import com.xianzhi.fridge.speech.api.VoiceDraftContracts;
import com.xianzhi.fridge.speech.config.SpeechProperties;
import com.xianzhi.fridge.speech.domain.VoiceStatus;
import com.xianzhi.fridge.speech.infrastructure.VoiceIngestion;
import com.xianzhi.fridge.speech.infrastructure.VoiceIngestionRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceDraftService {
    private final VoiceIngestionRepository drafts; private final FridgeRepository fridges; private final ObjectStoragePort storage;
    private final SpeechToTextPort speech; private final IngredientDraftParser parser; private final InventoryService inventory;
    private final IdempotencyService idempotency; private final OutboxEventRepository outbox; private final ObjectMapper mapper;
    private final SpeechProperties properties; private final Clock clock;
    public VoiceDraftService(VoiceIngestionRepository drafts, FridgeRepository fridges, ObjectStoragePort storage,
            SpeechToTextPort speech, IngredientDraftParser parser, InventoryService inventory, IdempotencyService idempotency,
            OutboxEventRepository outbox, ObjectMapper mapper, SpeechProperties properties, Clock clock) {
        this.drafts=drafts; this.fridges=fridges; this.storage=storage; this.speech=speech; this.parser=parser; this.inventory=inventory;
        this.idempotency=idempotency; this.outbox=outbox; this.mapper=mapper; this.properties=properties; this.clock=clock;
    }
    @Transactional
    public VoiceDraftContracts.View upload(UUID userId, UUID fridgeId, MultipartFile audio) {
        if (!speech.available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SPEECH_SERVICE_UNAVAILABLE", "Speech transcription is not configured");
        fridges.findById(fridgeId).filter(value -> userId.equals(value.getUserId()) && value.getDeletedAt()==null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"FRIDGE_NOT_FOUND","Fridge not found"));
        String type=audio.getContentType()==null?"":audio.getContentType().toLowerCase();
        if (audio.isEmpty() || audio.getSize()>properties.getMaxUploadBytes() || !type.startsWith("audio/"))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_AUDIO","Audio must be a non-empty audio file within the upload limit");
        UUID id=UuidV7.next(); String objectKey;
        try { objectKey=storage.store(userId,id,audio); } catch(IOException exception) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"OBJECT_STORAGE_UNAVAILABLE","Could not store audio"); }
        Instant now=clock.instant(); VoiceIngestion draft=drafts.save(new VoiceIngestion(id,userId,fridgeId,objectKey,audio.getOriginalFilename(),type,audio.getSize(),now));
        try { outbox.save(new OutboxEvent(UuidV7.next(),"VoiceIngestion",id,"VoiceIngestionRequested",mapper.writeValueAsString(Map.of("voiceDraftId",id)))); }
        catch(JsonProcessingException exception){throw new IllegalStateException(exception);}
        return view(draft);
    }
    @Transactional(readOnly=true)
    public VoiceDraftContracts.View get(UUID userId, UUID id) { return view(owned(userId,id)); }
    @Transactional
    public void process(UUID id) {
        VoiceIngestion draft=drafts.findById(id).orElseThrow(); if(draft.getStatus()!=VoiceStatus.UPLOADED) return;
        Instant now=clock.instant(); draft.transcribing(now);
        try { String transcript=speech.transcribe(draft.getObjectKey()); String json=mapper.writeValueAsString(parser.parse(transcript,draft.getFridgeId())); draft.ready(transcript,json,clock.instant()); }
        catch(RuntimeException|JsonProcessingException exception){ draft.fail("SPEECH_PROCESSING_FAILED",exception.getMessage()==null?"Speech processing failed":exception.getMessage(),clock.instant()); }
    }
    @Transactional
    public InventoryContracts.ItemView confirm(UUID userId, UUID id, String key, VoiceDraftContracts.ConfirmRequest request) {
        String path="/api/v1/inventory/voice-drafts/"+id+"/confirm";
        InventoryContracts.ItemView replay=idempotency.replay(userId,key,"POST",path,request,InventoryContracts.ItemView.class); if(replay!=null)return replay;
        VoiceIngestion draft=owned(userId,id); if(draft.getStatus()!=VoiceStatus.READY) throw new ApiException(HttpStatus.CONFLICT,"VOICE_DRAFT_NOT_READY","Voice draft is not ready for confirmation");
        if(!draft.getFridgeId().equals(request.inventory().fridgeId())) throw new ApiException(HttpStatus.BAD_REQUEST,"VOICE_DRAFT_FRIDGE_MISMATCH","Confirmed inventory must use the draft fridge");
        String nested="voice-"+Hashing.sha256(userId+":"+key).substring(0,64);
        InventoryContracts.ItemView response=inventory.createItem(userId,nested,request.inventory()); draft.confirm(clock.instant()); storage.delete(draft.getObjectKey());
        idempotency.save(userId,key,"POST",path,request,response,200); return response;
    }
    @Transactional
    public int expire() { int count=0; for(VoiceIngestion value:drafts.findTop100ByStatusInAndExpiresAtBefore(List.of(VoiceStatus.UPLOADED,VoiceStatus.TRANSCRIBING,VoiceStatus.READY,VoiceStatus.CONFIRMED),clock.instant())) { value.expire(clock.instant()); storage.delete(value.getObjectKey()); count++; } return count; }
    private VoiceIngestion owned(UUID userId,UUID id){return drafts.findByIdAndUserId(id,userId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"VOICE_DRAFT_NOT_FOUND","Voice draft not found"));}
    private VoiceDraftContracts.View view(VoiceIngestion value){JsonNode json=null;try{if(value.getDraftJson()!=null)json=mapper.readTree(value.getDraftJson());}catch(JsonProcessingException ignored){}return new VoiceDraftContracts.View(value.getId(),value.getFridgeId(),value.getStatus(),value.getTranscriptText(),json,value.getFailureCode(),value.getFailureReason(),value.getExpiresAt(),value.getConfirmedAt(),value.getCreatedAt(),value.getUpdatedAt());}
}
