package pl.neopak.rma.returnmanagement.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class SlaDeadline {

    private final LocalDate deadline;

    private SlaDeadline(LocalDate deadline) {
        if (deadline == null) {
            throw new IllegalArgumentException("deadline must not be null");
        }
        this.deadline = deadline;
    }

    public static SlaDeadline of(LocalDate deadline) {
        return new SlaDeadline(deadline);
    }

    public static SlaDeadline fromReceivedAt(Instant receivedAt, ZoneId zone) {
        LocalDate deadline = receivedAt.atZone(zone).toLocalDate().plusDays(14);
        return new SlaDeadline(deadline);
    }

    public static SlaDeadline fromReceivedAt(Instant receivedAt) {
        return fromReceivedAt(receivedAt, ZoneId.of("Europe/Warsaw"));
    }

    public boolean isBreached(Clock clock) {
        return LocalDate.now(clock).isAfter(deadline);
    }

    public long daysRemaining(Clock clock) {
        return ChronoUnit.DAYS.between(LocalDate.now(clock), deadline);
    }

    public LocalDate asLocalDate() {
        return deadline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SlaDeadline)) return false;
        SlaDeadline other = (SlaDeadline) o;
        return Objects.equals(deadline, other.deadline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deadline);
    }
}
