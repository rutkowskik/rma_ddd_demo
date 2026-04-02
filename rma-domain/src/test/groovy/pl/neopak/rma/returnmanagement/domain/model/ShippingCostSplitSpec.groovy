package pl.neopak.rma.returnmanagement.domain.model

import spock.lang.Specification
import spock.lang.Unroll

class ShippingCostSplitSpec extends Specification {

    @Unroll
    def "customer share uses ceiling rounding: totalGrosze=#totalGrosze, percent=#percent"() {
        given:
        def split = ShippingCostSplit.of(totalGrosze, percent)

        expect:
        split.customerShare() == expectedCustomer
        split.storeShare() == expectedStore

        where:
        totalGrosze | percent || expectedCustomer | expectedStore
        2000        | 50      || 1000             | 1000
        1999        | 50      || 1000             | 999
        1001        | 50      || 501              | 500
        1           | 50      || 1                | 0
        0           | 50      || 0                | 0
        2000        | 0       || 0                | 2000
        2000        | 100     || 2000             | 0
        999         | 33      || 330              | 669
    }

    @Unroll
    def "customerShare + storeShare always equals total: totalGrosze=#totalGrosze, percent=#percent"() {
        given:
        def split = ShippingCostSplit.of(totalGrosze, percent)

        expect:
        split.customerShare() + split.storeShare() == split.totalGrosze

        where:
        totalGrosze | percent
        2000        | 50
        1999        | 50
        1001        | 50
        1           | 50
        0           | 50
        2000        | 0
        2000        | 100
        999         | 33
    }

    def "negative total throws"() {
        when:
        ShippingCostSplit.of(-1, 50)

        then:
        thrown(IllegalArgumentException)
    }

    def "percent above 100 throws"() {
        when:
        ShippingCostSplit.of(1000, 101)

        then:
        thrown(IllegalArgumentException)
    }
}
