package pl.neopak.rma.returnmanagement.port.out;

import pl.neopak.rma.returnmanagement.domain.model.PackageDimensions;

public interface CourierGateway {
    String createShipment(PackageDimensions dimensions, String rmaNumber);
    byte[] getLabel(String shipmentId);
}
