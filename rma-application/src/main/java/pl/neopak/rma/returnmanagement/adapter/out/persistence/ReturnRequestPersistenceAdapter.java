package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import org.springframework.stereotype.Component;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;

import java.util.List;
import java.util.Optional;

@Component
public class ReturnRequestPersistenceAdapter implements ReturnRequestRepository {

    private final ReturnRequestJpaRepository jpaRepository;
    private final ReturnRequestMapper mapper;

    public ReturnRequestPersistenceAdapter(ReturnRequestJpaRepository jpaRepository,
                                           ReturnRequestMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(ReturnRequest returnRequest) {
        jpaRepository.save(mapper.toJpa(returnRequest));
    }

    @Override
    public Optional<ReturnRequest> findByRmaNumber(RmaNumber rmaNumber) {
        return jpaRepository.findByRmaNumber(rmaNumber.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrderId(String orderId, String sourceSystem) {
        return jpaRepository.existsByOrderIdAndSourceSystem(orderId, sourceSystem);
    }

    @Override
    public List<ReturnRequest> findByStatuses(List<ReturnStatus> statuses) {
        var statusNames = statuses.stream().map(Enum::name).toList();
        return jpaRepository.findByStatusIn(statusNames).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
