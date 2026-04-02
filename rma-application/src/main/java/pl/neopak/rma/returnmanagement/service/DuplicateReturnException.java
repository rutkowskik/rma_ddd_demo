package pl.neopak.rma.returnmanagement.service;
public class DuplicateReturnException extends RuntimeException {
    public DuplicateReturnException(String orderId) {
        super("Zgloszenie zwrotu dla zamowienia juz istnieje: " + orderId);
    }
}
