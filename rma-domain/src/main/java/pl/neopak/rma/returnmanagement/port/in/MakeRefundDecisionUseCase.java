package pl.neopak.rma.returnmanagement.port.in;
import pl.neopak.rma.returnmanagement.domain.model.RefundDecision;
public interface MakeRefundDecisionUseCase {
    void makeDecision(String rmaNumber, RefundDecision decision, int refundAmountGrosze, String decidedByUserId);
}
