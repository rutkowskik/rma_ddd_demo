package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record ReturnRejected(
        String rmaNumber,
        String rejectionReason,
        Instant occurredAt
) implements DomainEvent {}
