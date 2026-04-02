package pl.neopak.rma.returnmanagement.port.out;

public interface PaymentGateway {
    /**
     * Tworzy sesję płatności w PayU i zwraca URL do przekierowania klienta.
     */
    String createPaymentSession(String rmaNumber, int amountGrosze, String customerEmail);

    /**
     * Weryfikuje podpis MD5 webhooka PayU.
     * Signature = MD5(payload + second_key)
     */
    boolean validateWebhookSignature(String payload, String receivedSignature);

    /**
     * Realizuje zwrot środków dla danej sesji płatności.
     */
    void refund(String paymentSessionId, int amountGrosze);
}
