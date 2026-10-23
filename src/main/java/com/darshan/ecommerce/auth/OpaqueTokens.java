package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.common.crypto.Sha256;

import java.security.SecureRandom;
import java.util.Base64;

// Refresh tokens are random, not signed: 256 bits from SecureRandom, Base64url for transport,
// and only the SHA-256 digest is persisted so a database leak does not hand out valid tokens.
final class OpaqueTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {
    }

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        return Sha256.hex(rawToken);
    }
}
