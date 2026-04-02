package pl.neopak.rma.returnmanagement.domain.model

import spock.lang.Specification
import spock.lang.Unroll

class CustomerInfoSpec extends Specification {

    @Unroll
    def "valid emails are accepted: #email"() {
        when:
        def info = CustomerInfo.of(email, "Jan Kowalski")

        then:
        info.email() == email

        where:
        email << ["jan@example.com", "test.user@neopak.pl", "a@b.co"]
    }

    @Unroll
    def "invalid emails are rejected: #email"() {
        when:
        CustomerInfo.of(email, "Jan Kowalski")

        then:
        thrown(IllegalArgumentException)

        where:
        email << [null, "", "notanemail", "@nodomain", "noatsigncom"]
    }

    def "pseudonymize masks email correctly"() {
        given:
        def info = CustomerInfo.of("jan.kowalski@example.com", "Jan Kowalski")

        when:
        def masked = info.pseudonymize()

        then:
        masked.email() == "j***@example.com"
        masked.name() == "Jan ***"
    }

    def "blank name throws"() {
        when:
        CustomerInfo.of("jan@test.com", "")

        then:
        thrown(IllegalArgumentException)
    }
}
