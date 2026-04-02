package pl.neopak.rma.returnmanagement.service

import pl.neopak.rma.returnmanagement.domain.model.*
import pl.neopak.rma.returnmanagement.port.in.RegisterBlindReturnUseCase
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository
import pl.neopak.rma.returnmanagement.port.out.ShipmentTrackingRepository
import spock.lang.Specification
import java.time.Instant

class ReceiveShipmentServiceSpec extends Specification {

    ShipmentTrackingRepository trackingRepository = Mock()
    ReturnRequestRepository repository = Mock()
    RegisterBlindReturnUseCase registerBlindReturn = Mock()
    DomainEventPublisher eventPublisher = Mock()
    ReceiveShipmentService service = new ReceiveShipmentService(
        trackingRepository, repository, registerBlindReturn, eventPublisher)

    def "known tracking number receives shipment on existing RMA"() {
        given:
        def rma = aReturnRequestInTransit()
        trackingRepository.findByTrackingNumber("TRK-001") >> Optional.of(rma)

        when:
        service.receiveShipment("TRK-001", "worker-1")

        then:
        1 * repository.save(rma)
        1 * eventPublisher.publish(_)
        0 * registerBlindReturn.registerBlind(_, _)
    }

    def "unknown tracking number triggers blind return registration"() {
        given:
        trackingRepository.findByTrackingNumber("TRK-UNKNOWN") >> Optional.empty()

        when:
        service.receiveShipment("TRK-UNKNOWN", "worker-1")

        then:
        1 * registerBlindReturn.registerBlind({ it.contains("TRK-UNKNOWN") }, "worker-1")
        0 * repository.save(_)
    }

    def "partial receipt: one of two shipments received does not change status to RECEIVED"() {
        given:
        def rma = aReturnRequestWithTwoShipments()
        trackingRepository.findByTrackingNumber("TRK-001") >> Optional.of(rma)

        when:
        service.receiveShipment("TRK-001", "worker-1")

        then:
        rma.status() != ReturnStatus.RECEIVED  // tylko jedna z dwoch paczek
        1 * repository.save(rma)
    }

    private ReturnRequest aReturnRequestInTransit() {
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )
        rma.addShipment(PackageDimensions.of(10, 40, 30, 20))
        rma.confirmPayment("PAY-123")
        rma.assignLabels(["http://label1.pdf"])
        rma.pullEvents()
        rma
    }

    private ReturnRequest aReturnRequestWithTwoShipments() {
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )
        rma.addShipment(PackageDimensions.of(10, 40, 30, 20))
        rma.addShipment(PackageDimensions.of(15, 50, 35, 25))
        rma.confirmPayment("PAY-123")
        rma.assignLabels(["http://label1.pdf", "http://label2.pdf"])
        rma.pullEvents()
        rma
    }
}
