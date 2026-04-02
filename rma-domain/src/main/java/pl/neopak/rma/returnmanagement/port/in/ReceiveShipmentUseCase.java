package pl.neopak.rma.returnmanagement.port.in;

public interface ReceiveShipmentUseCase {
    void receiveShipment(String trackingNumber, String warehouseWorkerId);
}
