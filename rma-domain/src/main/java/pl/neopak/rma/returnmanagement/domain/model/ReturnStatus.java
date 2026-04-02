package pl.neopak.rma.returnmanagement.domain.model;

import pl.neopak.rma.returnmanagement.domain.exception.InvalidStatusTransitionException;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public enum ReturnStatus {

    PENDING_SHIPMENT,
    IN_TRANSIT,
    RECEIVED,
    VERIFICATION,
    DECISION,
    AWAITING_REFUND,
    REFUND_AND_DISPOSE,
    REJECTED,
    COMPLETED,
    BLIND_RECEIVED;

    private final Set<ReturnStatus> allowedTransitions = new HashSet<>();

    static {
        PENDING_SHIPMENT.allowedTransitions.addAll(EnumSet.of(IN_TRANSIT, DECISION));
        IN_TRANSIT.allowedTransitions.addAll(EnumSet.of(RECEIVED));
        RECEIVED.allowedTransitions.addAll(EnumSet.of(VERIFICATION));
        BLIND_RECEIVED.allowedTransitions.addAll(EnumSet.of(VERIFICATION));
        VERIFICATION.allowedTransitions.addAll(EnumSet.of(DECISION));
        DECISION.allowedTransitions.addAll(EnumSet.of(AWAITING_REFUND, REFUND_AND_DISPOSE, REJECTED));
        AWAITING_REFUND.allowedTransitions.addAll(EnumSet.of(COMPLETED));
        REFUND_AND_DISPOSE.allowedTransitions.addAll(EnumSet.of(COMPLETED));
    }

    public boolean canTransitionTo(ReturnStatus target) {
        return allowedTransitions.contains(target);
    }

    public ReturnStatus transitionTo(ReturnStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
        return target;
    }
}
