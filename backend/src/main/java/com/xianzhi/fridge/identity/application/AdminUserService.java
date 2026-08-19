package com.xianzhi.fridge.identity.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.identity.api.AdminUserContracts;
import com.xianzhi.fridge.identity.domain.UserRole;
import com.xianzhi.fridge.identity.domain.UserStatus;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.identity.infrastructure.RefreshSessionRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.infrastructure.AuditLog;
import com.xianzhi.fridge.shared.infrastructure.AuditLogRepository;
import com.xianzhi.fridge.shared.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final AppUserRepository users; private final RefreshSessionRepository sessions;
    private final AuditLogRepository audits; private final AuditService audit; private final IdempotencyService idempotency;
    private final IdentityProtector identity; private final PasswordEncoder passwords; private final ObjectMapper mapper;
    private final Clock clock;
    public AdminUserService(AppUserRepository users, RefreshSessionRepository sessions, AuditLogRepository audits,
            AuditService audit, IdempotencyService idempotency, IdentityProtector identity,
            PasswordEncoder passwords, ObjectMapper mapper, Clock clock) {
        this.users=users;this.sessions=sessions;this.audits=audits;this.audit=audit;this.idempotency=idempotency;
        this.identity=identity;this.passwords=passwords;this.mapper=mapper;this.clock=clock;
    }

    @Transactional(readOnly = true)
    public AdminUserContracts.PageView<AdminUserContracts.UserView> list(String query, UserRole role, String status,
                                                                         int page, int size) {
        int safePage=Math.max(0,page), safeSize=Math.max(1,Math.min(100,size));
        UserStatus userStatus=null; Boolean deleted=null;
        if(status!=null&&!status.isBlank()) {
            if("DELETED".equalsIgnoreCase(status)){deleted=true;}
            else { try{userStatus=UserStatus.valueOf(status.toUpperCase(Locale.ROOT));deleted=false;}
            catch(IllegalArgumentException exception){throw validation("Unknown user status");} }
        }
        String normalized=query==null||query.isBlank()?null:query.trim();
        Page<AppUser> result=users.search(normalized,role,userStatus,deleted,
                PageRequest.of(safePage,safeSize,Sort.by(Sort.Direction.DESC,"createdAt")));
        return new AdminUserContracts.PageView<>(result.getContent().stream().map(this::view).toList(),
                result.getTotalElements(),result.getNumber(),result.getSize(),result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminUserContracts.UserView get(UUID id){return view(find(id));}

    @Transactional(readOnly = true)
    public AdminUserContracts.PageView<AdminUserContracts.AuditView> auditLogs(UUID id,int page,int size){
        find(id);int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(100,size));
        Page<AuditLog> result=audits.findByTargetUserIdOrderByCreatedAtDesc(id,PageRequest.of(safePage,safeSize));
        return new AdminUserContracts.PageView<>(result.getContent().stream().map(this::auditView).toList(),
                result.getTotalElements(),result.getNumber(),result.getSize(),result.getTotalPages());
    }

    @Transactional
    public AdminUserContracts.ActionView status(UUID actor,UUID id,String key,AdminUserContracts.StatusRequest request){
        String path="/api/v1/admin/users/"+id+"/status";
        return idempotent(actor,key,"PATCH",path,request,()->{
            destructiveSelf(actor,id); List<AppUser> activeAdmins=users.lockAvailableAdmins(); AppUser target=lock(id);
            if(target.getDeletedAt()!=null)throw conflict("USER_DELETED","Deleted users must be restored first");
            if(target.getStatus()==UserStatus.ACTIVE&&request.status()==UserStatus.DISABLED)protectLastAdmin(target,activeAdmins);
            target.changeStatus(request.status()); target.invalidateAccessTokens(); if(request.status()==UserStatus.DISABLED)revoke(id);
            audit.record(actor,id,"ADMIN_USER_STATUS_CHANGED",Map.of("status",request.status().name()));
            return action(target,"STATUS_CHANGED");
        });
    }

    @Transactional
    public AdminUserContracts.ActionView role(UUID actor,UUID id,String key,AdminUserContracts.RoleRequest request){
        String path="/api/v1/admin/users/"+id+"/role";
        return idempotent(actor,key,"PATCH",path,request,()->{
            destructiveSelf(actor,id); List<AppUser> activeAdmins=users.lockAvailableAdmins(); AppUser target=lock(id);
            if(target.getDeletedAt()!=null)throw conflict("USER_DELETED","Deleted users cannot change role");
            if(target.getRole()==UserRole.ADMIN&&request.role()==UserRole.USER)protectLastAdmin(target,activeAdmins);
            target.changeRole(request.role()); target.invalidateAccessTokens(); revoke(id);
            audit.record(actor,id,"ADMIN_USER_ROLE_CHANGED",Map.of("role",request.role().name()));
            return action(target,"ROLE_CHANGED");
        });
    }

    @Transactional
    public AdminUserContracts.ActionView revokeSessions(UUID actor,UUID id,String key){
        String path="/api/v1/admin/users/"+id+"/sessions/revoke";
        return idempotent(actor,key,"POST",path,id,()->{
            destructiveSelf(actor,id);AppUser target=lock(id);target.invalidateAccessTokens();revoke(id);
            audit.record(actor,id,"ADMIN_USER_SESSIONS_REVOKED",Map.of());return action(target,"SESSIONS_REVOKED");
        });
    }

    @Transactional
    public AdminUserContracts.TemporaryPasswordView resetPassword(UUID actor,UUID id,String key){
        IdempotencyService.requireKey(key);destructiveSelf(actor,id);AppUser target=lock(id);
        if(!target.isAvailable())throw conflict("USER_UNAVAILABLE","Only active users can receive a temporary password");
        String keyHash=Hashing.sha256(actor+":"+id+":"+key);
        String raw="Xz!"+identity.hmac("password-reset:"+actor+":"+id+":"+key).substring(0,21);
        if(!keyHash.equals(target.getTemporaryPasswordKeyHash())){
            Instant expires=clock.instant().plus(Duration.ofHours(24));
            target.requireTemporaryPassword(passwords.encode(raw),expires,keyHash);revoke(id);
            audit.record(actor,id,"ADMIN_USER_PASSWORD_RESET",Map.of("expiresAt",expires.toString()));
        }
        return new AdminUserContracts.TemporaryPasswordView(id,raw,target.getTemporaryPasswordExpiresAt());
    }

    @Transactional
    public AdminUserContracts.ActionView delete(UUID actor,UUID id,String key){
        String path="/api/v1/admin/users/"+id;
        return idempotent(actor,key,"DELETE",path,id,()->{
            destructiveSelf(actor,id);List<AppUser> activeAdmins=users.lockAvailableAdmins();AppUser target=lock(id);if(target.getDeletedAt()==null){protectLastAdmin(target,activeAdmins);target.softDelete(clock.instant());target.invalidateAccessTokens();revoke(id);}
            audit.record(actor,id,"ADMIN_USER_DELETED",Map.of("retentionDays",90));return action(target,"DELETED");
        });
    }

    @Transactional
    public AdminUserContracts.ActionView restore(UUID actor,UUID id,String key){
        String path="/api/v1/admin/users/"+id+"/restore";
        return idempotent(actor,key,"POST",path,id,()->{
            AppUser target=lock(id);if(target.getDeletedAt()==null)throw conflict("USER_NOT_DELETED","User is not deleted");
            if(target.getAnonymizedAt()!=null)throw conflict("USER_ANONYMIZED","Anonymized users cannot be restored");
            target.restore();audit.record(actor,id,"ADMIN_USER_RESTORED",Map.of());return action(target,"RESTORED");
        });
    }

    private <T> T idempotent(UUID actor,String key,String method,String path,Object request,Supplier<T> operation){
        @SuppressWarnings("unchecked") Class<T> type=(Class<T>)AdminUserContracts.ActionView.class;
        T replay=idempotency.replay(actor,key,method,path,request,type);if(replay!=null)return replay;
        T response=operation.get();idempotency.save(actor,key,method,path,request,response,200);return response;
    }
    private void destructiveSelf(UUID actor,UUID target){if(actor.equals(target))throw conflict("ADMIN_SELF_ACTION_FORBIDDEN","Use your own account settings for this action");}
    private void protectLastAdmin(AppUser target,List<AppUser> active){
        if(target.getRole()!=UserRole.ADMIN||target.getStatus()!=UserStatus.ACTIVE||target.getDeletedAt()!=null)return;
        if(active.size()<=1)throw conflict("LAST_ADMIN_REQUIRED","At least one active administrator is required");
    }
    private void revoke(UUID id){Instant now=clock.instant();sessions.findByUserIdAndRevokedAtIsNull(id).forEach(value->value.revoke(now,null));}
    private AppUser lock(UUID id){return users.lockById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found"));}
    private AppUser find(UUID id){return users.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found"));}
    private AdminUserContracts.UserView view(AppUser value){return new AdminUserContracts.UserView(value.getId(),value.getUsername(),value.getEmail(),value.getDisplayName(),value.getRole(),value.getStatus(),!value.onboardingRequired(),value.isPasswordChangeRequired(),sessions.countByUserIdAndRevokedAtIsNull(value.getId()),value.getLastLoginAt(),value.getCreatedAt(),value.getUpdatedAt(),value.getDeletedAt(),value.getDeletionRequestedAt(),value.getAnonymizedAt());}
    private AdminUserContracts.ActionView action(AppUser value,String action){return new AdminUserContracts.ActionView(value.getId(),action,value.getRole(),value.getStatus(),clock.instant());}
    private AdminUserContracts.AuditView auditView(AuditLog value){JsonNode metadata=mapper.createObjectNode();try{if(value.getMetadataJson()!=null)metadata=mapper.readTree(value.getMetadataJson());}catch(Exception ignored){}return new AdminUserContracts.AuditView(value.getId(),value.getUserId(),value.getTargetUserId(),value.getEventType(),value.getTraceId(),metadata,value.getCreatedAt());}
    private static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    private static ApiException validation(String message){return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
}
