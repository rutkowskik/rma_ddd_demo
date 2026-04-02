package pl.neopak.rma.returnmanagement.port.in;
import pl.neopak.rma.returnmanagement.domain.model.ConditionAssessment;
import java.util.List;
public interface AssessConditionUseCase {
    void assessCondition(String rmaNumber, List<ConditionAssessment> assessments, String assessedByUserId);
}
