package pl.neopak.rma.returnmanagement.service

import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException
import pl.neopak.rma.returnmanagement.domain.model.*
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository
import spock.lang.Specification

import java.time.Instant

class MakeRefundDecisionServiceSpec extends Specification {

    ReturnRequestRepository repository = Mock()
    DomainEventPublisher eventPublisher = Mock()
    MakeRefundDecisionService service = new MakeRefundDecisionService(repository, eventPublisher)

    def "should transition to AWAITING_REFUND for REFUND_AND_RETURN decision"() {
        given:
        def rma = rmaInDecision()
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)

        when:
        service.makeDecision("ZWR-00001", RefundDecision.REFUND_AND_RETURN, 5000, "bok-1")

        then:
        1 * repository.save({ it.status() == ReturnStatus.AWAITING_REFUND })
        1 * eventPublisher.publish(_)
    }

    def "should transition to REFUND_AND_DISPOSE without creating shipment"() {
        given:
        def rma = rmaInDecision()
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)

        when:
        service.makeDecision("ZWR-00001", RefundDecision.REFUND_AND_DISPOSE, 5000, "bok-1")

        then:
        1 * repository.save({ it.status() == ReturnStatus.REFUND_AND_DISPOSE })
        1 * eventPublisher.publish(_)
    }

    def "should transition to REJECTED for REJECTION decision"() {
        given:
        def rma = rmaInDecision()
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)

        when:
        service.makeDecision("ZWR-00001", RefundDecision.REJECTION, 0, "bok-1")

        then:
        1 * repository.save({ it.status() == ReturnStatus.REJECTED })
        1 * eventPublisher.publish(_)
    }

    def "should throw RmaNotFoundException when rma not found"() {
        given:
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.empty()

        when:
        service.makeDecision("ZWR-99999", RefundDecision.REFUND_AND_RETURN, 5000, "bok-1")

        then:
        thrown(RmaNotFoundException)
        0 * repository.save(_)
    }

    private ReturnRequest rmaInDecision() {
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORD-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@test.pl", "Jan Kowalski")
        )
        rma.addLineItem("PROD-1", 1, ReturnReason.DAMAGED)
        rma.addShipment(new PackageDimensions(5, 30, 20, 10))
        rma.confirmPayment("sess-1")
        rma.assignLabels(["http://label.pdf"])
        rma.receiveShipment("TRK-001", "worker-1", Instant.now())
        rma.startVerification()
        rma.assessCondition([ConditionAssessment.DAMAGED])
        rma.pullEvents()
        return rma
    }
}
