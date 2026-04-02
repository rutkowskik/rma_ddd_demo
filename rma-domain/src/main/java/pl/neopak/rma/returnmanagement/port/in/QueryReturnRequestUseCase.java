package pl.neopak.rma.returnmanagement.port.in;

import java.util.List;
import java.util.Optional;

public interface QueryReturnRequestUseCase {
    Optional<Object> findByRmaNumber(String rmaNumber);
    List<Object> findByStatus(String status);
}
