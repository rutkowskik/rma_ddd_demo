package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReturnRequestJpaRepository extends JpaRepository<ReturnRequestJpaEntity, UUID> {

    Optional<ReturnRequestJpaEntity> findByRmaNumber(String rmaNumber);

    boolean existsByOrderIdAndSourceSystem(String orderId, String sourceSystem);

    List<ReturnRequestJpaEntity> findByStatusIn(List<String> statuses);
}
