package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.model.RefundDecision;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException;
import pl.neopak.rma.returnmanagement.port.in.MakeRefundDecisionUseCase;
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;

@Service
public class MakeRefundDecisionService implements MakeRefundDecisionUseCase {

    private final ReturnRequestRepository repository;
    private final DomainEventPublisher eventPublisher;

    public MakeRefundDecisionService(ReturnRequestRepository repository,
                                      DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void makeDecision(String rmaNumber, RefundDecision decision,
                              int refundAmountGrosze, String decidedByUserId) {
        var rma = repository.findByRmaNumber(RmaNumber.of(rmaNumber))
            .orElseThrow(() -> new RmaNotFoundException(rmaNumber));
        rma.makeRefundDecision(decision, refundAmountGrosze, decidedByUserId);
        repository.save(rma);
        rma.pullEvents().forEach(eventPublisher::publish);
    }
}
