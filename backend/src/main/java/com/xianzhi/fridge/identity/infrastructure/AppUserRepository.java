package com.xianzhi.fridge.identity.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailAndDeletedAtIsNull(String email);
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByUsernameAndDeletedAtIsNull(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id and user.deletedAt is null")
    Optional<AppUser> lockActiveById(UUID id);
}
