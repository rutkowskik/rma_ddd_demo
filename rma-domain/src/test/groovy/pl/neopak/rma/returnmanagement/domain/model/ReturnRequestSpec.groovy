package pl.neopak.rma.returnmanagement.domain.model

import pl.neopak.rma.returnmanagement.domain.event.BlindReturnRegistered
import pl.neopak.rma.returnmanagement.domain.event.ReturnLabelGenerated
import pl.neopak.rma.returnmanagement.domain.event.ReturnRequestCreated
import pl.neopak.rma.returnmanagement.domain.exception.InvalidStatusTransitionException
import pl.neopak.rma.returnmanagement.domain.exception.LabelGenerationBeforePaymentException
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

class ReturnRequestSpec extends Specification {

    // --- Metody pomocnicze ---

    private ReturnRequest aReturnRequest() {
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )
        rma.pullEvents()
        rma
    }

    private ReturnRequest aReturnRequestInTransit() {
        def rma = aReturnRequest()
        rma.addShipment(PackageDimensions.of(10, 40, 30, 20))
        rma.confirmPayment("PAY-123")
        rma.assignLabels(["http://label1.pdf"])
        rma.pullEvents()
        rma
    }

    private ReturnRequest aReturnRequestInDecision() {
        def rma = aReturnRequestInTransit()
        rma.receiveShipment("TRK-001", "worker-1", Instant.now())
        rma.startVerification()
        rma.addLineItem("PROD-1", 2, ReturnReason.DAMAGED)
        rma.assessCondition([ConditionAssessment.DAMAGED])
        rma.pullEvents()
        rma
    }

    // --- Scenariusze testowe ---

    def "create() emits ReturnRequestCreated with rmaNumber and customerEmail"() {
        when:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )

        then:
        def events = rma.pullEvents()
        events.size() == 1
        def event = events[0] as ReturnRequestCreated
        event.rmaNumber() == "ZWR-00001"
        event.customerEmail() == "jan@example.com"
    }

    def "created return starts in PENDING_SHIPMENT status"() {
        when:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )

        then:
        rma.status() == ReturnStatus.PENDING_SHIPMENT
    }

    def "registerBlind() starts in BLIND_RECEIVED and emits BlindReturnRegistered"() {
        when:
        def rma = ReturnRequest.registerBlind(RmaNumber.of("ZWR-00002"), "Paczka bez dokumentow")

        then:
        rma.status() == ReturnStatus.BLIND_RECEIVED
        def events = rma.pullEvents()
        events.size() == 1
        def event = events[0] as BlindReturnRegistered
        event.rmaNumber() == "ZWR-00002"
        event.parcelDescription() == "Paczka bez dokumentow"
    }

    def "assignLabels() before payment throws LabelGenerationBeforePaymentException"() {
        given:
        def rma = aReturnRequest()
        rma.addShipment(PackageDimensions.of(10, 40, 30, 20))

        when:
        rma.assignLabels(["http://label1.pdf"])

        then:
        thrown(LabelGenerationBeforePaymentException)
    }

    def "confirmPayment() sets paymentConfirmed to true"() {
        given:
        def rma = aReturnRequest()

        when:
        rma.confirmPayment("PAY-123")

        then:
        rma.isPaymentConfirmed() == true
    }

    def "assignLabels() after payment transitions to IN_TRANSIT"() {
        given:
        def rma = aReturnRequest()
        rma.addShipment(PackageDimensions.of(10, 40, 30, 20))
        rma.confirmPayment("PAY-123")

        when:
        rma.assignLabels(["http://label1.pdf"])

        then:
        rma.status() == ReturnStatus.IN_TRANSIT
    }

    def "assignLabels() emits ReturnLabelGenerated with labelUrls"() {
        given:
        def rma = aReturnRequest()
        rma.addShipment(PackageDimensions.of(10, 40, 30, 20))
        rma.confirmPayment("PAY-123")

        when:
        rma.assignLabels(["http://label1.pdf"])

        then:
        def events = rma.pullEvents()
        def labelEvent = events.find { it instanceof ReturnLabelGenerated } as ReturnLabelGenerated
        labelEvent != null
        labelEvent.labelUrls() == ["http://label1.pdf"]
    }

    def "receiveShipment() when all received transitions to RECEIVED"() {
        given:
        def rma = aReturnRequestInTransit()

        when:
        rma.receiveShipment("TRK-001", "worker-1", Instant.now())

        then:
        rma.status() == ReturnStatus.RECEIVED
    }

    def "receiveShipment() sets slaDeadline to 14 days from receivedAt"() {
        given:
        def rma = aReturnRequestInTransit()
        def receivedAt = Instant.now()

        when:
        rma.receiveShipment("TRK-001", "worker-1", receivedAt)

        then:
        def sla = rma.slaDeadline()
        sla.isPresent()
        def expectedDeadline = SlaDeadline.fromReceivedAt(receivedAt)
        sla.get() == expectedDeadline
    }

    @Unroll
    def "assessCondition() requires VERIFICATION status — throws when status is #invalidStatus"() {
        given:
        def rma = aReturnRequest()
        // Ustaw status na nieprawidlowy bez przechodzenia przez normalna sciezke
        // Uzywamy pomocniczej metody, ktora prowadzi do konkretnych stanow
        ReturnRequest target = buildRmaInStatus(invalidStatus)

        when:
        target.assessCondition([ConditionAssessment.DAMAGED])

        then:
        thrown(InvalidStatusTransitionException)

        where:
        invalidStatus << [ReturnStatus.DECISION, ReturnStatus.RECEIVED, ReturnStatus.IN_TRANSIT]
    }

    def "makeRefundDecision(REFUND_AND_DISPOSE) transitions to REFUND_AND_DISPOSE"() {
        given:
        def rma = aReturnRequestInDecision()

        when:
        rma.makeRefundDecision(RefundDecision.REFUND_AND_DISPOSE, 5000, "user-1")

        then:
        rma.status() == ReturnStatus.REFUND_AND_DISPOSE
    }

    def "makeRefundDecision(REFUND_AND_RETURN) transitions to AWAITING_REFUND"() {
        given:
        def rma = aReturnRequestInDecision()

        when:
        rma.makeRefundDecision(RefundDecision.REFUND_AND_RETURN, 5000, "user-1")

        then:
        rma.status() == ReturnStatus.AWAITING_REFUND
    }

    def "makeRefundDecision(REJECTION) transitions to REJECTED"() {
        given:
        def rma = aReturnRequestInDecision()

        when:
        rma.makeRefundDecision(RefundDecision.REJECTION, 0, "user-1")

        then:
        rma.status() == ReturnStatus.REJECTED
    }

    def "pullEvents() returns events and clears the list"() {
        given:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )

        when:
        def firstPull = rma.pullEvents()
        def secondPull = rma.pullEvents()

        then:
        firstPull.size() == 1
        secondPull.size() == 0
    }

    def "adding second shipment increases shipments count"() {
        given:
        def rma = aReturnRequest()
        rma.addShipment(PackageDimensions.of(10, 40, 30, 20))

        when:
        rma.addShipment(PackageDimensions.of(5, 20, 15, 10))

        then:
        rma.shipments().size() == 2
    }

    // --- Metoda pomocnicza do budowania RMA w okreslonym statusie ---

    private ReturnRequest buildRmaInStatus(ReturnStatus targetStatus) {
        switch (targetStatus) {
            case ReturnStatus.IN_TRANSIT:
                return aReturnRequestInTransit()
            case ReturnStatus.RECEIVED:
                def rma = aReturnRequestInTransit()
                rma.receiveShipment("TRK-001", "worker-1", Instant.now())
                rma.pullEvents()
                return rma
            case ReturnStatus.DECISION:
                return aReturnRequestInDecision()
            default:
                return aReturnRequest()
        }
    }
}
