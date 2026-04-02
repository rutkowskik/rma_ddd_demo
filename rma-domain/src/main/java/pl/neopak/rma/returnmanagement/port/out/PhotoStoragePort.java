package pl.neopak.rma.returnmanagement.port.out;

public interface PhotoStoragePort {
    String store(byte[] photoBytes, String rmaNumber, String filename);
    void delete(String url);
}
