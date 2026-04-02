package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record ReturnRequestCreated(
        String rmaNumber,
        String orderId,
        String sourceSystem,
        String customerEmail,
        Instant occurredAt
) implements DomainEvent {}
