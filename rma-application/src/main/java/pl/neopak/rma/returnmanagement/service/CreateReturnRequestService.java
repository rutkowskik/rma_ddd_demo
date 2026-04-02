package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.in.CreateReturnRequestCommand;
import pl.neopak.rma.returnmanagement.port.in.CreateReturnRequestUseCase;
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;
import pl.neopak.rma.returnmanagement.port.out.RmaNumberGenerator;

@Service
public class CreateReturnRequestService implements CreateReturnRequestUseCase {

    private final ReturnRequestRepository repository;
    private final RmaNumberGenerator numberGenerator;
    private final DomainEventPublisher eventPublisher;

    public CreateReturnRequestService(ReturnRequestRepository repository,
                                       RmaNumberGenerator numberGenerator,
                                       DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RmaNumber create(CreateReturnRequestCommand command) {
        if (repository.existsByOrderId(
                command.orderReference().orderId(),
                command.orderReference().sourceSystem().name())) {
            throw new DuplicateReturnException(command.orderReference().orderId());
        }
        var rmaNumber = numberGenerator.generate();
        var returnRequest = ReturnRequest.create(rmaNumber, command.orderReference(), command.customerInfo());
        repository.save(returnRequest);
        returnRequest.pullEvents().forEach(eventPublisher::publish);
        return rmaNumber;
    }
}
