package pl.neopak.rma.returnmanagement.service

import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException
import pl.neopak.rma.returnmanagement.domain.model.*
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository
import spock.lang.Specification

import java.time.Instant

class AssessConditionServiceSpec extends Specification {

    ReturnRequestRepository repository = Mock()
    DomainEventPublisher eventPublisher = Mock()
    AssessConditionService service = new AssessConditionService(repository, eventPublisher)

    def "should assess condition and transition to DECISION"() {
        given:
        def rma = rmaInVerification()
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)

        when:
        service.assessCondition("ZWR-00001", [ConditionAssessment.DAMAGED], "worker-1")

        then:
        1 * repository.save({ it.status() == ReturnStatus.DECISION })
        1 * eventPublisher.publish(_)
    }

    def "should throw RmaNotFoundException when rma not found"() {
        given:
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.empty()

        when:
        service.assessCondition("ZWR-99999", [ConditionAssessment.NEW], "worker-1")

        then:
        thrown(RmaNotFoundException)
        0 * repository.save(_)
    }

    def "should assess with multiple condition assessments"() {
        given:
        def rma = rmaInVerification()
        rma.addLineItem("PROD-2", 1, ReturnReason.DAMAGED)
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)

        when:
        service.assessCondition("ZWR-00001", [ConditionAssessment.DAMAGED, ConditionAssessment.FOR_RESALE], "worker-1")

        then:
        1 * repository.save({ it.status() == ReturnStatus.DECISION })
    }

    private ReturnRequest rmaInVerification() {
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
        rma.pullEvents()
        return rma
    }
}
