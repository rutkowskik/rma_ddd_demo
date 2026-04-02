package pl.neopak.rma.returnmanagement.domain.exception;

public class LabelGenerationBeforePaymentException extends RuntimeException {

    public LabelGenerationBeforePaymentException(String rmaNumber) {
        super("Nie mozna wygenerowac etykiety przed potwierdzeniem platnosci dla: " + rmaNumber);
    }
}
