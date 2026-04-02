package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.exception.LabelGenerationBeforePaymentException;
import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.in.GenerateLabelUseCase;
import pl.neopak.rma.returnmanagement.port.out.CourierGateway;
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;

import java.util.List;

@Service
public class GenerateLabelService implements GenerateLabelUseCase {

    private final ReturnRequestRepository repository;
    private final CourierGateway courierGateway;
    private final DomainEventPublisher eventPublisher;

    public GenerateLabelService(ReturnRequestRepository repository,
                                 CourierGateway courierGateway,
                                 DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.courierGateway = courierGateway;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<String> generateLabels(String rmaNumber) {
        var rma = repository.findByRmaNumber(RmaNumber.of(rmaNumber))
            .orElseThrow(() -> new RmaNotFoundException(rmaNumber));
        // rzuca LabelGenerationBeforePaymentException jesli brak platnosci (sprawdz przed wywolaniem kuriera)
        if (!rma.isPaymentConfirmed()) {
            throw new LabelGenerationBeforePaymentException(rmaNumber);
        }
        // generuj etykiety dla kazdej paczki
        var labelUrls = rma.shipments().stream()
            .map(shipment -> courierGateway.createShipment(shipment.getDimensions(), rmaNumber))
            .toList();
        rma.assignLabels(labelUrls);
        repository.save(rma);
        rma.pullEvents().forEach(eventPublisher::publish);
        return labelUrls;
    }
}
