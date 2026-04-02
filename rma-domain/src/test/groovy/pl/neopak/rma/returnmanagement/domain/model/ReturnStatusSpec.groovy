package pl.neopak.rma.returnmanagement.domain.model

import pl.neopak.rma.returnmanagement.domain.exception.InvalidStatusTransitionException
import spock.lang.Specification
import spock.lang.Unroll

class ReturnStatusSpec extends Specification {

    @Unroll
    def "valid transitions are allowed: #from -> #to"() {
        expect:
        from.canTransitionTo(to) == true

        where:
        from                            | to
        ReturnStatus.PENDING_SHIPMENT   | ReturnStatus.IN_TRANSIT
        ReturnStatus.PENDING_SHIPMENT   | ReturnStatus.DECISION
        ReturnStatus.IN_TRANSIT         | ReturnStatus.RECEIVED
        ReturnStatus.RECEIVED           | ReturnStatus.VERIFICATION
        ReturnStatus.BLIND_RECEIVED     | ReturnStatus.VERIFICATION
        ReturnStatus.VERIFICATION       | ReturnStatus.DECISION
        ReturnStatus.DECISION           | ReturnStatus.AWAITING_REFUND
        ReturnStatus.DECISION           | ReturnStatus.REFUND_AND_DISPOSE
        ReturnStatus.DECISION           | ReturnStatus.REJECTED
        ReturnStatus.AWAITING_REFUND    | ReturnStatus.COMPLETED
        ReturnStatus.REFUND_AND_DISPOSE | ReturnStatus.COMPLETED
    }

    @Unroll
    def "invalid transitions are rejected: #from -> #to"() {
        expect:
        from.canTransitionTo(to) == false

        where:
        from                            | to
        ReturnStatus.PENDING_SHIPMENT   | ReturnStatus.COMPLETED
        ReturnStatus.IN_TRANSIT         | ReturnStatus.DECISION
        ReturnStatus.RECEIVED           | ReturnStatus.COMPLETED
        ReturnStatus.REJECTED           | ReturnStatus.COMPLETED
        ReturnStatus.COMPLETED          | ReturnStatus.PENDING_SHIPMENT
    }

    def "transitionTo throws for invalid transition"() {
        when:
        ReturnStatus.PENDING_SHIPMENT.transitionTo(ReturnStatus.COMPLETED)

        then:
        thrown(InvalidStatusTransitionException)
    }
}
