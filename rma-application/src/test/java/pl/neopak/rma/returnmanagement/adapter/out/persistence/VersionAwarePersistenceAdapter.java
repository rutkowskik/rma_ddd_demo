package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Adapter testowy nadpisujacy produkcyjny ReturnRequestPersistenceAdapter.
 * Rozwiazuje problem optymistycznego blokowania wynikajacy z tego,
 * ze produkcyjny mapper zawsze tworzy encje JPA z version=0.
 *
 * Przed kazdym save() odczytuje aktualny numer wersji z bazy danych
 * i ustawia go w encji, dzieki czemu merge() w Hibernate dziala poprawnie.
 */
@Primary
@TestComponent
public class VersionAwarePersistenceAdapter implements ReturnRequestRepository {

    private final ReturnRequestJpaRepository jpaRepository;
    private final ReturnRequestMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public VersionAwarePersistenceAdapter(ReturnRequestJpaRepository jpaRepository,
                                          ReturnRequestMapper mapper,
                                          JdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(ReturnRequest returnRequest) {
        var entity = mapper.toJpa(returnRequest);
        List<Long> versions = jdbcTemplate.queryForList(
                "SELECT version FROM return_management.return_requests WHERE id = ?::uuid",
                Long.class,
                entity.getId().toString()
        );
        if (!versions.isEmpty()) {
            entity.setVersion(versions.get(0));
        }
        jpaRepository.save(entity);
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
