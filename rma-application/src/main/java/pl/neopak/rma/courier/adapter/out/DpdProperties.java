package pl.neopak.rma.courier.adapter.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "courier.dpd")
public record DpdProperties(
        String baseUrl,
        String login,
        String password,
        String fid
) {}
