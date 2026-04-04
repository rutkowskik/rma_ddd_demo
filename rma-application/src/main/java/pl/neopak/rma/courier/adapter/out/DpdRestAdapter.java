package pl.neopak.rma.courier.adapter.out;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.neopak.rma.returnmanagement.domain.model.PackageDimensions;
import pl.neopak.rma.returnmanagement.port.out.CourierGateway;

import java.util.Map;

@Component
@Qualifier("dpd")
public class DpdRestAdapter implements CourierGateway {

    private final DpdProperties properties;
    private final RestClient restClient;

    @Autowired
    public DpdRestAdapter(DpdProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    // Constructor for testing — allows injecting a pre-configured RestClient
    DpdRestAdapter(DpdProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String createShipment(PackageDimensions dimensions, String rmaNumber) {
        Map<String, Object> body = Map.of(
                "login", properties.login(),
                "password", properties.password(),
                "fid", Integer.parseInt(properties.fid()),
                "shipment", Map.of(
                        "reference", rmaNumber,
                        "content", "Zwrot RMA " + rmaNumber,
                        "weight", dimensions.getWeightKg(),
                        "size", Map.of(
                                "x", dimensions.getLengthCm(),
                                "y", dimensions.getWidthCm(),
                                "z", dimensions.getHeightCm()
                        ),
                        "services", Map.of("type", "RETURN")
                )
        );

        ShipmentResponse response = restClient.post()
                .uri("/services/shipment")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ShipmentResponse.class);

        if (response == null || response.shipmentId() == null) {
            throw new IllegalStateException("DPD did not return shipment id");
        }
        return response.shipmentId();
    }

    @Override
    public byte[] getLabel(String shipmentId) {
        byte[] pdf = restClient.get()
                .uri("/services/label/{shipmentId}", shipmentId)
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body(byte[].class);

        if (pdf == null || pdf.length == 0) {
            throw new IllegalStateException("DPD returned empty label for shipment " + shipmentId);
        }
        return pdf;
    }

    private record ShipmentResponse(String shipmentId, String parcelNumber) {}
}
