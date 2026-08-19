package com.xianzhi.fridge.identity.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityTombstoneRepository extends JpaRepository<IdentityTombstone, UUID> {
    boolean existsByUsernameHmac(String usernameHmac);
    boolean existsByEmailHmac(String emailHmac);
}
