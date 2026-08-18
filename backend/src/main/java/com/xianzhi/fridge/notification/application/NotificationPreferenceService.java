package com.xianzhi.fridge.notification.application;

import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.notification.api.NotificationPreferenceContracts;
import com.xianzhi.fridge.notification.infrastructure.NotificationPreference;
import com.xianzhi.fridge.notification.infrastructure.NotificationPreferenceRepository;
import com.xianzhi.fridge.notification.infrastructure.NotificationType;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.web.ApiException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {
    private final NotificationPreferenceRepository preferences; private final AppUserRepository users; private final IdempotencyService idempotency; private final Clock clock;
    public NotificationPreferenceService(NotificationPreferenceRepository preferences,AppUserRepository users,IdempotencyService idempotency,Clock clock){this.preferences=preferences;this.users=users;this.idempotency=idempotency;this.clock=clock;}
    @Transactional
    public List<NotificationPreferenceContracts.View> get(UUID userId){String timezone=users.findById(userId).orElseThrow().getTimezone();Map<NotificationType,NotificationPreference> existing=new EnumMap<>(NotificationType.class);preferences.findByUserIdOrderByTypeAsc(userId).forEach(value->existing.put(value.getType(),value));List<NotificationPreferenceContracts.View> output=new ArrayList<>();for(NotificationType type:NotificationType.values()){NotificationPreference value=existing.get(type);output.add(value==null?new NotificationPreferenceContracts.View(type,true,false,null,null,timezone):view(value));}return output;}
    @Transactional
    public List<NotificationPreferenceContracts.View> update(UUID userId,String key,NotificationPreferenceContracts.UpdateRequest request){String path="/api/v1/me/notification-preferences";List<?> replay=idempotency.replay(userId,key,"PUT",path,request,List.class);if(replay!=null)return get(userId);Set<NotificationType> seen=EnumSet.noneOf(NotificationType.class);for(NotificationPreferenceContracts.Entry entry:request.preferences()){if(!seen.add(entry.type()))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Duplicate notification preference type");String timezone=entry.timezone()==null?users.findById(userId).orElseThrow().getTimezone():entry.timezone();try{ZoneId.of(timezone);}catch(RuntimeException invalid){throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Unknown timezone");}NotificationPreference value=preferences.findByUserIdAndType(userId,entry.type()).orElseGet(()->new NotificationPreference(UuidV7.next(),userId,entry.type(),timezone,clock.instant()));value.update(entry.inAppEnabled(),entry.emailEnabled(),entry.quietStart(),entry.quietEnd(),timezone,clock.instant());preferences.save(value);}List<NotificationPreferenceContracts.View> result=get(userId);idempotency.save(userId,key,"PUT",path,request,result,200);return result;}
    private NotificationPreferenceContracts.View view(NotificationPreference value){return new NotificationPreferenceContracts.View(value.getType(),value.isInAppEnabled(),value.isEmailEnabled(),value.getQuietStart(),value.getQuietEnd(),value.getTimezone());}
}
