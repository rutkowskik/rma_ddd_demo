package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record ReturnCompleted(
        String rmaNumber,
        Instant occurredAt
) implements DomainEvent {}
