package com.xianzhi.fridge.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() { }

    public static UUID next() {
        long timestamp = System.currentTimeMillis() & 0x0000_FFFF_FFFF_FFFFL;
        long mostSignificantBits = (timestamp << 16) | 0x7000L | RANDOM.nextInt(0x1000);
        long leastSignificantBits = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
        leastSignificantBits |= 0x8000_0000_0000_0000L;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
