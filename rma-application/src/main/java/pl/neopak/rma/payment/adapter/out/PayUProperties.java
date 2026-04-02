package pl.neopak.rma.payment.adapter.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payu")
public record PayUProperties(
    String baseUrl,
    String posId,
    String md5Key,
    String oauthClientId,
    String oauthClientSecret
) {}
