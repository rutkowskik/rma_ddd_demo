package pl.neopak.rma.returnmanagement.domain.model

import spock.lang.Specification
import spock.lang.Unroll

class PackageDimensionsSpec extends Specification {

    @Unroll
    def "courier eligibility rules: #weightKg kg, #lengthCm x #widthCm x #heightCm cm via #courier -> #eligible"() {
        expect:
        PackageDimensions.of(weightKg, lengthCm, widthCm, heightCm).isSuitableForCourier(courier) == eligible

        where:
        weightKg | lengthCm | widthCm | heightCm | courier          || eligible
        20       | 60       | 40      | 40       | CourierCode.INPOST || true
        26       | 60       | 40      | 40       | CourierCode.INPOST || false
        25       | 60       | 40      | 40       | CourierCode.INPOST || true
        65       | 65       | 40      | 40       | CourierCode.INPOST || false
        31       | 80       | 60      | 60       | CourierCode.DPD    || true
        32       | 80       | 60      | 60       | CourierCode.DPD    || false
        150      | 120      | 80      | 80       | CourierCode.GEIS   || true
        150      | 120      | 80      | 80       | CourierCode.DPD    || false
        40       | 100      | 60      | 60       | CourierCode.GLS    || true
        41       | 100      | 60      | 60       | CourierCode.GLS    || false
        30       | 80       | 60      | 60       | CourierCode.ORLEN  || true
        31       | 80       | 60      | 60       | CourierCode.ORLEN  || false
    }

    def "zero weight throws"() {
        when:
        PackageDimensions.of(0, 60, 40, 40)

        then:
        thrown(IllegalArgumentException)
    }
}
