package pl.neopak.rma.courier.adapter.out;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Fabryka testowa do tworzenia InPostRestAdapter z wymuszonym HTTP/1.1 (SimpleClientHttpRequestFactory).
 * Wymagana, poniewaz domyslny klient HTTP w Java 21 probowaC uzyc HTTP/2,
 * co jest niekompatybilne z WireMock standalone w trybie testowym.
 */
public final class InPostAdapterFactory {

    private InPostAdapterFactory() {}

    public static InPostRestAdapter withHttp11(InPostProperties properties) {
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        return new InPostRestAdapter(properties, restClient);
    }
}
