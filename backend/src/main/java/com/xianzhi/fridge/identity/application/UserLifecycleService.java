package com.xianzhi.fridge.identity.application;

import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.identity.infrastructure.IdentityTombstone;
import com.xianzhi.fridge.identity.infrastructure.IdentityTombstoneRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.config.AppProperties;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.speech.application.ObjectStoragePort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLifecycleService {
    private final AppUserRepository users;private final IdentityTombstoneRepository tombstones;
    private final IdentityProtector identity;private final ObjectStoragePort storage;private final JdbcTemplate jdbc;
    private final AuditService audit;private final AppProperties properties;private final Clock clock;
    public UserLifecycleService(AppUserRepository users,IdentityTombstoneRepository tombstones,IdentityProtector identity,
            ObjectStoragePort storage,JdbcTemplate jdbc,AuditService audit,AppProperties properties,Clock clock){
        this.users=users;this.tombstones=tombstones;this.identity=identity;this.storage=storage;this.jdbc=jdbc;
        this.audit=audit;this.properties=properties;this.clock=clock;
    }
    @Transactional public int anonymizeDue(){
        Instant now=clock.instant(),cutoff=now.minus(properties.getIdentity().getDeletionRetention());int count=0;
        for(AppUser user:users.findTop100ByDeletedAtBeforeAndAnonymizedAtIsNullOrderByDeletedAtAsc(cutoff)){
            UUID id=user.getId();
            tombstones.save(new IdentityTombstone(UuidV7.next(),id,identity.hmac(user.getUsername()),identity.hmac(user.getEmail()),now));
            List<String> objects=jdbc.query("select object_key from voice_ingestion where user_id=UUID_TO_BIN(?)",(rs,n)->rs.getString(1),id.toString());
            objects.forEach(storage::delete);
            jdbc.update("update voice_ingestion set object_key=concat('anonymized:',BIN_TO_UUID(id)),original_filename=null,transcript_text=null,draft_json=null,failure_reason=null,updated_at=UTC_TIMESTAMP(3) where user_id=UUID_TO_BIN(?)",id.toString());
            jdbc.update("update user_preference set tastes=JSON_ARRAY(),cuisines=JSON_ARRAY(),allergies=JSON_ARRAY(),dislikes=JSON_ARRAY(),dietary_goal=null,calorie_target=null,updated_at=UTC_TIMESTAMP(3) where user_id=UUID_TO_BIN(?)",id.toString());
            jdbc.update("update assistant_message set content='[已删除]',page=null,selection_json=JSON_OBJECT(),citations_json=JSON_ARRAY() where user_id=UUID_TO_BIN(?)",id.toString());
            jdbc.update("update ai_context_snapshot set context_json=JSON_OBJECT(),source_versions=JSON_ARRAY() where user_id=UUID_TO_BIN(?)",id.toString());
            jdbc.update("update assistant_action_proposal set payload_json=JSON_OBJECT(),result_json=null,status='DISMISSED',dismissed_at=coalesce(dismissed_at,UTC_TIMESTAMP(3)) where user_id=UUID_TO_BIN(?)",id.toString());
            String compact=id.toString().replace("-","");
            user.anonymize("deleted_"+compact.substring(0,24),"deleted+"+compact+"@invalid.local","已删除用户",now);
            audit.record(null,id,"USER_ANONYMIZED",Map.of("retentionDays",properties.getIdentity().getDeletionRetention().toDays()));count++;
        }
        return count;
    }
}
