package pl.neopak.rma.returnmanagement.domain.exception;

public class RmaNotFoundException extends RuntimeException {

    public RmaNotFoundException(String rmaNumber) {
        super("Nie znaleziono zgloszenia RMA: " + rmaNumber);
    }
}
