package pl.neopak.rma.returnmanagement.port.in;

public interface ConfirmPaymentUseCase {
    void confirmPayment(String rmaNumber, String paymentSessionId);
}
