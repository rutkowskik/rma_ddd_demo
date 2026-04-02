package pl.neopak.rma.returnmanagement.port.in;
import pl.neopak.rma.returnmanagement.domain.model.OrderReference;
import pl.neopak.rma.returnmanagement.domain.model.CustomerInfo;
public record CreateReturnRequestCommand(
    OrderReference orderReference,
    CustomerInfo customerInfo
) {}
