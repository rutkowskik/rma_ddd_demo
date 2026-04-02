package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.model.ConditionAssessment;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException;
import pl.neopak.rma.returnmanagement.port.in.AssessConditionUseCase;
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;
import java.util.List;

@Service
public class AssessConditionService implements AssessConditionUseCase {

    private final ReturnRequestRepository repository;
    private final DomainEventPublisher eventPublisher;

    public AssessConditionService(ReturnRequestRepository repository,
                                   DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void assessCondition(String rmaNumber, List<ConditionAssessment> assessments, String assessedByUserId) {
        var rma = repository.findByRmaNumber(RmaNumber.of(rmaNumber))
            .orElseThrow(() -> new RmaNotFoundException(rmaNumber));
        rma.assessCondition(assessments);
        repository.save(rma);
        rma.pullEvents().forEach(eventPublisher::publish);
    }
}
