package pl.neopak.rma.payment.adapter.out;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import pl.neopak.rma.returnmanagement.port.out.PaymentGateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties(PayUProperties.class)
public class PayURestAdapter implements PaymentGateway {

    private final PayUProperties properties;
    private final RestClient restClient;

    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public PayURestAdapter(PayUProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    // Constructor for testing — allows injecting a pre-configured RestClient
    PayURestAdapter(PayUProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String createPaymentSession(String rmaNumber, int amountGrosze, String customerEmail) {
        String token = getBearerToken();

        Map<String, Object> body = Map.of(
                "notifyUrl", "https://neopak.pl/api/payu/notify",
                "customerIp", "127.0.0.1",
                "merchantPosId", properties.posId(),
                "description", "RMA " + rmaNumber,
                "currencyCode", "PLN",
                "totalAmount", String.valueOf(amountGrosze),
                "buyer", Map.of("email", customerEmail),
                "products", List.of(Map.of(
                        "name", "Zwrot RMA " + rmaNumber,
                        "unitPrice", String.valueOf(amountGrosze),
                        "quantity", "1"
                ))
        );

        OrderResponse response = restClient.post()
                .uri("/api/v2_1/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OrderResponse.class);

        if (response == null || response.redirectUri() == null) {
            throw new IllegalStateException("PayU did not return redirectUri");
        }
        return response.redirectUri();
    }

    @Override
    public boolean validateWebhookSignature(String payload, String receivedSignature) {
        String input = payload + properties.md5Key();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(digest);
            return computed.equals(receivedSignature);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    @Override
    public void refund(String paymentSessionId, int amountGrosze) {
        String token = getBearerToken();

        Map<String, Object> body = Map.of(
                "refund", Map.of(
                        "description", "RMA refund",
                        "amount", amountGrosze
                )
        );

        restClient.post()
                .uri("/api/v2_1/orders/{sessionId}/refunds", paymentSessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private synchronized String getBearerToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.oauthClientId());
        form.add("client_secret", properties.oauthClientSecret());

        OAuthTokenResponse tokenResponse = restClient.post()
                .uri("/pl/standard/user/oauth/authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OAuthTokenResponse.class);

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("Failed to obtain PayU OAuth token");
        }

        cachedToken = tokenResponse.accessToken();
        tokenExpiry = Instant.now().plusSeconds(tokenResponse.expiresIn() - 10);
        return cachedToken;
    }

    private record OAuthTokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        public String accessToken() { return accessToken; }

        @com.fasterxml.jackson.annotation.JsonProperty("token_type")
        public String tokenType() { return tokenType; }

        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        public long expiresIn() { return expiresIn; }
    }

    private record OrderResponse(
            String redirectUri,
            String orderId
    ) {}
}
