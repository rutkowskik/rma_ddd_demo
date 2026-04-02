package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record RefundDecisionMade(
        String rmaNumber,
        String decision,
        int refundAmountGrosze,
        Instant occurredAt
) implements DomainEvent {}
