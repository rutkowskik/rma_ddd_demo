package pl.neopak.rma.returnmanagement.domain.event;

import java.time.Instant;

public record ReturnShipmentReceived(
        String rmaNumber,
        String trackingNumber,
        String warehouseWorkerId,
        Instant occurredAt
) implements DomainEvent {}
