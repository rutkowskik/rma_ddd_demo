package pl.neopak.rma.returnmanagement.port.in;

import java.util.List;

public interface GenerateLabelUseCase {
    List<String> generateLabels(String rmaNumber);
}
