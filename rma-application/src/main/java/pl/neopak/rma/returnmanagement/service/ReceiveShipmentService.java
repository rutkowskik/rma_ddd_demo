package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.in.ReceiveShipmentUseCase;
import pl.neopak.rma.returnmanagement.port.in.RegisterBlindReturnUseCase;
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;
import pl.neopak.rma.returnmanagement.port.out.RmaNumberGenerator;
import pl.neopak.rma.returnmanagement.port.out.ShipmentTrackingRepository;
import java.time.Instant;

@Service
public class ReceiveShipmentService implements ReceiveShipmentUseCase {

    private final ShipmentTrackingRepository trackingRepository;
    private final ReturnRequestRepository repository;
    private final RegisterBlindReturnUseCase registerBlindReturn;
    private final DomainEventPublisher eventPublisher;

    public ReceiveShipmentService(ShipmentTrackingRepository trackingRepository,
                                   ReturnRequestRepository repository,
                                   RegisterBlindReturnUseCase registerBlindReturn,
                                   DomainEventPublisher eventPublisher) {
        this.trackingRepository = trackingRepository;
        this.repository = repository;
        this.registerBlindReturn = registerBlindReturn;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void receiveShipment(String trackingNumber, String warehouseWorkerId) {
        var rmaOpt = trackingRepository.findByTrackingNumber(trackingNumber);
        if (rmaOpt.isPresent()) {
            var rma = rmaOpt.get();
            rma.receiveShipment(trackingNumber, warehouseWorkerId, Instant.now());
            repository.save(rma);
            rma.pullEvents().forEach(eventPublisher::publish);
        } else {
            // slepe zwrot — brak etykiety RMA
            registerBlindReturn.registerBlind(
                "Nieznana paczka, numer listu: " + trackingNumber, warehouseWorkerId);
        }
    }
}
