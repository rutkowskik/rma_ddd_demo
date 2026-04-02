package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.in.ConfirmPaymentUseCase;
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;

@Service
public class ConfirmPaymentService implements ConfirmPaymentUseCase {

    private final ReturnRequestRepository repository;
    private final DomainEventPublisher eventPublisher;

    public ConfirmPaymentService(ReturnRequestRepository repository, DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void confirmPayment(String rmaNumber, String paymentSessionId) {
        var rma = repository.findByRmaNumber(RmaNumber.of(rmaNumber))
            .orElseThrow(() -> new RmaNotFoundException(rmaNumber));
        rma.confirmPayment(paymentSessionId);
        repository.save(rma);
        rma.pullEvents().forEach(eventPublisher::publish);
    }
}
