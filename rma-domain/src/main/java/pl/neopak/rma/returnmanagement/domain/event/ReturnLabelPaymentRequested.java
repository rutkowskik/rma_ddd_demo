package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record ReturnLabelPaymentRequested(
        String rmaNumber,
        int amountGrosze,
        Instant occurredAt
) implements DomainEvent {}
