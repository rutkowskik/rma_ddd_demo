package pl.neopak.rma.courier.adapter.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "courier.inpost")
public record InPostProperties(
        String baseUrl,
        String apiKey,
        String organizationId
) {}
