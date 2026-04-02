package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;
import java.time.LocalDate;

public record SlaBreachWarningTriggered(
        String rmaNumber,
        LocalDate deadline,
        long daysRemaining,
        Instant occurredAt
) implements DomainEvent {}
