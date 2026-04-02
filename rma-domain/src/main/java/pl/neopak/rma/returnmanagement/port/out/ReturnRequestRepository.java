package pl.neopak.rma.returnmanagement.port.out;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;
import java.util.List;
import java.util.Optional;
public interface ReturnRequestRepository {
    void save(ReturnRequest returnRequest);
    Optional<ReturnRequest> findByRmaNumber(RmaNumber rmaNumber);
    boolean existsByOrderId(String orderId, String sourceSystem);
    List<ReturnRequest> findByStatuses(List<ReturnStatus> statuses);
}
