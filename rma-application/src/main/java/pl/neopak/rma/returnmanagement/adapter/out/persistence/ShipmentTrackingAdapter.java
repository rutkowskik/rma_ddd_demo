package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import org.springframework.stereotype.Component;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.port.out.ShipmentTrackingRepository;

import java.util.Optional;

@Component
public class ShipmentTrackingAdapter implements ShipmentTrackingRepository {

    private final ReturnRequestJpaRepository jpaRepository;
    private final ReturnRequestMapper mapper;

    public ShipmentTrackingAdapter(ReturnRequestJpaRepository jpaRepository,
                                   ReturnRequestMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ReturnRequest> findByTrackingNumber(String trackingNumber) {
        return jpaRepository.findAll().stream()
                .filter(entity -> entity.getShipments().stream()
                        .anyMatch(s -> trackingNumber.equals(s.getTrackingNumber())))
                .findFirst()
                .map(mapper::toDomain);
    }
}
