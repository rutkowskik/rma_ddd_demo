package pl.neopak.rma.returnmanagement.port.out;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import java.util.Optional;
public interface ShipmentTrackingRepository {
    Optional<ReturnRequest> findByTrackingNumber(String trackingNumber);
}
