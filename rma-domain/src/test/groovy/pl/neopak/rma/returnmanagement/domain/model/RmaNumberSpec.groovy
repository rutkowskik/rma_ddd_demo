package pl.neopak.rma.returnmanagement.domain.model

import spock.lang.Specification
import spock.lang.Unroll

class RmaNumberSpec extends Specification {

    @Unroll
    def "valid RMA numbers are accepted: #input"() {
        when:
        def rma = RmaNumber.of(input)

        then:
        rma.value() == input

        where:
        input       | _
        "ZWR-00001" | _
        "ZWR-99999" | _
        "ZWR-12345" | _
    }

    @Unroll
    def "invalid formats throw IllegalArgumentException: #invalid"() {
        when:
        RmaNumber.of(invalid)

        then:
        thrown(IllegalArgumentException)

        where:
        invalid       | _
        "ZWR-1"       | _
        "ZWR-000001"  | _
        "RMA-00001"   | _
        ""            | _
        null          | _
        "ZWR-ABCDE"   | _
        "zwr-00001"   | _
    }

    def "equality is based on value"() {
        expect:
        RmaNumber.of("ZWR-00001") == RmaNumber.of("ZWR-00001")
        RmaNumber.of("ZWR-00001") != RmaNumber.of("ZWR-00002")
    }

    def "hashCode is consistent with equals"() {
        expect:
        RmaNumber.of("ZWR-00001").hashCode() == RmaNumber.of("ZWR-00001").hashCode()
    }
}
