package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record BlindReturnRegistered(
        String rmaNumber,
        String parcelDescription,
        Instant occurredAt
) implements DomainEvent {}
