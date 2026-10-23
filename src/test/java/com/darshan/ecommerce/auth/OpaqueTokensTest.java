package com.darshan.ecommerce.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokensTest {

    @Test
    void generatesUrlSafeTokensOf256Bits() {
        String token = OpaqueTokens.generate();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void generatesDistinctTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(OpaqueTokens.generate());
        }

        assertThat(tokens).hasSize(1000);
    }

    @Test
    void hashesDeterministicallyToLowercaseHexSha256() {
        String hash = OpaqueTokens.hash("abc");

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(OpaqueTokens.hash("abc")).isEqualTo(hash);
        assertThat(OpaqueTokens.hash("abd")).isNotEqualTo(hash);
    }
}
