package com.xianzhi.fridge.identity.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import com.xianzhi.fridge.identity.domain.UserRole;
import com.xianzhi.fridge.identity.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailAndDeletedAtIsNull(String email);
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByUsernameAndDeletedAtIsNull(String username);

    @Query("select user from AppUser user where user.id = :id and user.status = com.xianzhi.fridge.identity.domain.UserStatus.ACTIVE and user.deletedAt is null and user.anonymizedAt is null")
    Optional<AppUser> findAvailableById(UUID id);

    @Query("select user from AppUser user where " +
            "(:query is null or lower(user.username) like lower(concat('%', :query, '%')) or lower(user.email) like lower(concat('%', :query, '%')) or lower(user.displayName) like lower(concat('%', :query, '%'))) and " +
            "(:role is null or user.role = :role) and " +
            "(:status is null or user.status = :status) and " +
            "(:deleted is null or (:deleted = true and user.deletedAt is not null) or (:deleted = false and user.deletedAt is null))")
    Page<AppUser> search(String query, UserRole role, UserStatus status, Boolean deleted, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id and user.status = com.xianzhi.fridge.identity.domain.UserStatus.ACTIVE and user.deletedAt is null and user.anonymizedAt is null")
    Optional<AppUser> lockActiveById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id")
    Optional<AppUser> lockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.role = com.xianzhi.fridge.identity.domain.UserRole.ADMIN and user.status = com.xianzhi.fridge.identity.domain.UserStatus.ACTIVE and user.deletedAt is null order by user.id")
    List<AppUser> lockAvailableAdmins();

    List<AppUser> findTop100ByDeletedAtBeforeAndAnonymizedAtIsNullOrderByDeletedAtAsc(Instant cutoff);
}
