package com.xianzhi.fridge.identity.application;

import com.xianzhi.fridge.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class IdentityProtector {
    private final byte[] key;
    public IdentityProtector(AppProperties properties) {
        this.key = properties.getIdentity().getTombstoneKey().getBytes(StandardCharsets.UTF_8);
    }
    public String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            StringBuilder result = new StringBuilder(64);
            for (byte item : mac.doFinal(value.getBytes(StandardCharsets.UTF_8))) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available", exception);
        }
    }
}
