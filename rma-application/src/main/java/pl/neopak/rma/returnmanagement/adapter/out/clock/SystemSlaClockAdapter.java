package pl.neopak.rma.returnmanagement.adapter.out.clock;

import org.springframework.stereotype.Component;
import pl.neopak.rma.returnmanagement.port.out.SlaClockPort;

import java.time.Instant;

@Component
public class SystemSlaClockAdapter implements SlaClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
