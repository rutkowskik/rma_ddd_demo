package pl.neopak.rma.returnmanagement.domain.exception;

import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ReturnStatus from, ReturnStatus to) {
        super("Niedozwolone przejscie statusu: " + from + " -> " + to);
    }
}
