package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.in.RegisterBlindReturnUseCase;
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;
import pl.neopak.rma.returnmanagement.port.out.RmaNumberGenerator;

@Service
public class RegisterBlindReturnService implements RegisterBlindReturnUseCase {

    private final ReturnRequestRepository repository;
    private final RmaNumberGenerator numberGenerator;
    private final DomainEventPublisher eventPublisher;

    public RegisterBlindReturnService(ReturnRequestRepository repository,
                                       RmaNumberGenerator numberGenerator,
                                       DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String registerBlind(String parcelDescription, String warehouseWorkerId) {
        var rmaNumber = numberGenerator.generate();
        var rma = ReturnRequest.registerBlind(rmaNumber, parcelDescription);
        repository.save(rma);
        rma.pullEvents().forEach(eventPublisher::publish);
        return rmaNumber.value();
    }
}
