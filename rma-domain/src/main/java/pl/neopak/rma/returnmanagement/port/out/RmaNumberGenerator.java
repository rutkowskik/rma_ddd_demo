package pl.neopak.rma.returnmanagement.port.out;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
public interface RmaNumberGenerator {
    RmaNumber generate();
}
