package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;
import java.util.List;

public record ReturnLabelGenerated(
        String rmaNumber,
        List<String> labelUrls,
        Instant occurredAt
) implements DomainEvent {}
