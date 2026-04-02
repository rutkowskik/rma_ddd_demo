package pl.neopak.rma.returnmanagement.domain.model

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SlaDeadlineSpec extends Specification {

    private Clock clockAt(LocalDate date) {
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    }

    @Unroll
    def "isBreached returns correct result"() {
        given:
        def deadline = SlaDeadline.of(LocalDate.of(2026, 4, 15))

        expect:
        deadline.isBreached(clockAt(clockDate)) == breached

        where:
        clockDate                 || breached
        LocalDate.of(2026, 4, 14) || false
        LocalDate.of(2026, 4, 15) || false
        LocalDate.of(2026, 4, 16) || true
        LocalDate.of(2026, 5, 1)  || true
    }

    def "daysRemaining calculates correctly"() {
        given:
        def deadline = SlaDeadline.of(LocalDate.of(2026, 4, 15))

        expect:
        deadline.daysRemaining(clockAt(LocalDate.of(2026, 4, 12))) == 3
        deadline.daysRemaining(clockAt(LocalDate.of(2026, 4, 15))) == 0
        deadline.daysRemaining(clockAt(LocalDate.of(2026, 4, 16))) == -1
    }

    def "fromReceivedAt adds 14 days"() {
        given:
        Instant received = Instant.parse("2026-04-01T10:00:00Z")

        expect:
        SlaDeadline.fromReceivedAt(received).asLocalDate() == LocalDate.of(2026, 4, 15)
    }
}
