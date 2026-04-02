package pl.neopak.rma.payment.adapter.out;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PayURestAdapterTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    private PayURestAdapter buildAdapter() {
        PayUProperties props = new PayUProperties(
                "http://localhost:" + wireMock.port(),
                "300746",
                "testkey",
                "300746",
                "secret"
        );
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        return new PayURestAdapter(props, restClient);
    }

    private void stubOAuth() {
        wireMock.stubFor(post(urlEqualTo("/pl/standard/user/oauth/authorize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-token\",\"token_type\":\"bearer\",\"expires_in\":3600}")));
    }

    // --- Test 1: createPaymentSession happy path ---

    @Test
    void createPaymentSession_returnsRedirectUri() {
        stubOAuth();
        wireMock.stubFor(post(urlEqualTo("/api/v2_1/orders"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"redirectUri\":\"https://payu.com/pay/abc123\",\"orderId\":\"ORDER-1\",\"status\":{\"statusCode\":\"SUCCESS\"}}")));

        PayURestAdapter adapter = buildAdapter();
        String url = adapter.createPaymentSession("RMA-001", 5000, "test@example.com");

        assertThat(url).isEqualTo("https://payu.com/pay/abc123");
    }

    // --- Test 2: validateWebhookSignature — correct signature ---

    @Test
    void validateWebhookSignature_correctSignature_returnsTrue() throws Exception {
        PayUProperties props = new PayUProperties(
                "http://localhost:" + wireMock.port(),
                "300746",
                "testkey",
                "300746",
                "secret"
        );
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        PayURestAdapter adapter = new PayURestAdapter(props, restClient);

        String payload = "{\"orderId\":\"test\"}";
        String input = payload + "testkey";
        MessageDigest md = MessageDigest.getInstance("MD5");
        String expectedSig = HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));

        assertThat(adapter.validateWebhookSignature(payload, expectedSig)).isTrue();
    }

    // --- Test 3: validateWebhookSignature — wrong signature ---

    @Test
    void validateWebhookSignature_wrongSignature_returnsFalse() {
        PayURestAdapter adapter = buildAdapter();
        assertThat(adapter.validateWebhookSignature("{\"orderId\":\"test\"}", "wrongsig")).isFalse();
    }

    // --- Test 4: refund happy path ---

    @Test
    void refund_happyPath_noExceptionThrown() {
        stubOAuth();
        wireMock.stubFor(post(urlEqualTo("/api/v2_1/orders/SESSION-1/refunds"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"refund\":{\"refundId\":\"R-1\",\"status\":\"PENDING\"}}")));

        PayURestAdapter adapter = buildAdapter();
        assertThatCode(() -> adapter.refund("SESSION-1", 5000)).doesNotThrowAnyException();
    }

    // --- Test 5: OAuth token is cached (called only once for two requests) ---

    @Test
    void createPaymentSession_twice_oauthCalledOnlyOnce() {
        stubOAuth();
        wireMock.stubFor(post(urlEqualTo("/api/v2_1/orders"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"redirectUri\":\"https://payu.com/pay/abc123\",\"orderId\":\"ORDER-1\",\"status\":{\"statusCode\":\"SUCCESS\"}}")));

        PayURestAdapter adapter = buildAdapter();
        adapter.createPaymentSession("RMA-001", 5000, "test@example.com");
        adapter.createPaymentSession("RMA-002", 3000, "other@example.com");

        wireMock.verify(1, postRequestedFor(urlEqualTo("/pl/standard/user/oauth/authorize")));
    }
}
