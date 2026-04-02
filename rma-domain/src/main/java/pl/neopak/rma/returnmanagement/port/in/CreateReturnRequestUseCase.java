package pl.neopak.rma.returnmanagement.port.in;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
public interface CreateReturnRequestUseCase {
    RmaNumber create(CreateReturnRequestCommand command);
}
