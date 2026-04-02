package pl.neopak.rma.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "rma-super-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";
    private static final long EXPIRATION_MS = 86400000L;

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_extractSubject_returnsCorrectSubject() {
        String token = provider.generateToken("user@neopak.pl", "CUSTOMER");

        assertThat(provider.extractSubject(token)).isEqualTo("user@neopak.pl");
    }

    @Test
    void generateToken_extractRole_returnsCorrectRole() {
        String token = provider.generateToken("worker@neopak.pl", "WAREHOUSE_WORKER");

        assertThat(provider.extractRole(token)).isEqualTo("WAREHOUSE_WORKER");
    }

    @Test
    void validateToken_freshToken_returnsTrue() {
        String token = provider.generateToken("admin@neopak.pl", "ADMIN");

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_wrongSignature_returnsFalse() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "completely-different-secret-key-that-is-at-least-256-bits-long-yes", EXPIRATION_MS);
        String tokenFromOtherProvider = otherProvider.generateToken("user@neopak.pl", "CUSTOMER");

        assertThat(provider.validateToken(tokenFromOtherProvider)).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L);
        String token = shortLivedProvider.generateToken("user@neopak.pl", "CUSTOMER");

        Thread.sleep(10);

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }
}
