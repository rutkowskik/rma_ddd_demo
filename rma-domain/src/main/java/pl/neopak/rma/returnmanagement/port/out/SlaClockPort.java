package pl.neopak.rma.returnmanagement.port.out;

import java.time.Instant;

public interface SlaClockPort {
    Instant now();
}
