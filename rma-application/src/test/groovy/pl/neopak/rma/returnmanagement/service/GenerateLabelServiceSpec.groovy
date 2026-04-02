package pl.neopak.rma.returnmanagement.service

import pl.neopak.rma.returnmanagement.domain.exception.LabelGenerationBeforePaymentException
import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException
import pl.neopak.rma.returnmanagement.domain.model.*
import pl.neopak.rma.returnmanagement.port.out.CourierGateway
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository
import spock.lang.Specification

class GenerateLabelServiceSpec extends Specification {

    ReturnRequestRepository repository = Mock()
    CourierGateway courierGateway = Mock()
    DomainEventPublisher eventPublisher = Mock()
    GenerateLabelService service = new GenerateLabelService(repository, courierGateway, eventPublisher)

    def "should generate label for single package after payment"() {
        given:
        def rma = rmaWithPaymentConfirmed(1)
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)
        courierGateway.createShipment(_ as PackageDimensions, "ZWR-00001") >> "http://label-1.pdf"

        when:
        def labels = service.generateLabels("ZWR-00001")

        then:
        labels == ["http://label-1.pdf"]
        1 * repository.save({ it.status() == ReturnStatus.IN_TRANSIT })
        1 * eventPublisher.publish(_)
    }

    def "should generate multiple labels for multi-package shipment"() {
        given:
        def rma = rmaWithPaymentConfirmed(3)
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)
        courierGateway.createShipment(_ as PackageDimensions, "ZWR-00001") >>> ["http://l1.pdf", "http://l2.pdf", "http://l3.pdf"]

        when:
        def labels = service.generateLabels("ZWR-00001")

        then:
        labels.size() == 3
        3 * courierGateway.createShipment(_, _)
        1 * repository.save(_)
    }

    def "should throw LabelGenerationBeforePaymentException when payment not confirmed"() {
        given:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORD-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@test.pl", "Jan Kowalski")
        )
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.of(rma)

        when:
        service.generateLabels("ZWR-00001")

        then:
        thrown(LabelGenerationBeforePaymentException)
        0 * courierGateway.createShipment(_, _)
        0 * repository.save(_)
    }

    def "should throw RmaNotFoundException when rma not found"() {
        given:
        repository.findByRmaNumber(_ as RmaNumber) >> Optional.empty()

        when:
        service.generateLabels("ZWR-99999")

        then:
        thrown(RmaNotFoundException)
    }

    private ReturnRequest rmaWithPaymentConfirmed(int packageCount) {
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORD-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@test.pl", "Jan Kowalski")
        )
        packageCount.times { rma.addShipment(new PackageDimensions(5, 30, 20, 10)) }
        rma.confirmPayment("sess-1")
        rma.pullEvents()
        return rma
    }
}
