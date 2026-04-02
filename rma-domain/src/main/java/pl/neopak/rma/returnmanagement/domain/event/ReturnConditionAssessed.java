package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record ReturnConditionAssessed(
        String rmaNumber,
        String condition,
        Instant occurredAt
) implements DomainEvent {}
