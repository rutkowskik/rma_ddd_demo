package pl.neopak.rma.courier.adapter.out;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.neopak.rma.returnmanagement.domain.model.PackageDimensions;
import pl.neopak.rma.returnmanagement.port.out.CourierGateway;

import java.util.Map;

@Component
@Primary
public class InPostRestAdapter implements CourierGateway {

    private final InPostProperties properties;
    private final RestClient restClient;

    @Autowired
    public InPostRestAdapter(InPostProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    // Constructor for testing — allows injecting a pre-configured RestClient
    InPostRestAdapter(InPostProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String createShipment(PackageDimensions dimensions, String rmaNumber) {
        Map<String, Object> body = Map.of(
                "reference", rmaNumber,
                "parcel", Map.of(
                        "weight", Map.of("amount", dimensions.getWeightKg(), "unit", "kg"),
                        "dimensions", Map.of(
                                "length", dimensions.getLengthCm(),
                                "width", dimensions.getWidthCm(),
                                "height", dimensions.getHeightCm(),
                                "unit", "cm"
                        )
                ),
                "service", "inpost_courier_return",
                "sender", Map.of("email", "rma@neopak.pl")
        );

        ShipmentResponse response = restClient.post()
                .uri("/v1/organizations/{orgId}/shipments", properties.organizationId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ShipmentResponse.class);

        if (response == null || response.id() == null) {
            throw new IllegalStateException("InPost did not return shipment id");
        }
        return response.id();
    }

    @Override
    public byte[] getLabel(String shipmentId) {
        byte[] pdf = restClient.get()
                .uri("/v1/shipments/{id}/label", shipmentId)
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body(byte[].class);

        if (pdf == null || pdf.length == 0) {
            throw new IllegalStateException("InPost returned empty label for shipment " + shipmentId);
        }
        return pdf;
    }

    private record ShipmentResponse(String id, String status, String trackingNumber) {}
}
