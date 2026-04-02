package pl.neopak.rma.returnmanagement.port.out;

import pl.neopak.rma.returnmanagement.domain.event.DomainEvent;

public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
