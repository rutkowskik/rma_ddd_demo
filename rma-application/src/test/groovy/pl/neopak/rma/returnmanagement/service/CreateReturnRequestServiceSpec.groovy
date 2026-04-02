package pl.neopak.rma.returnmanagement.service

import pl.neopak.rma.returnmanagement.domain.event.DomainEvent
import pl.neopak.rma.returnmanagement.domain.event.ReturnRequestCreated
import pl.neopak.rma.returnmanagement.domain.model.*
import pl.neopak.rma.returnmanagement.port.in.CreateReturnRequestCommand
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository
import pl.neopak.rma.returnmanagement.port.out.RmaNumberGenerator
import spock.lang.Specification

class CreateReturnRequestServiceSpec extends Specification {

    ReturnRequestRepository repository = Mock()
    RmaNumberGenerator numberGenerator = Stub { generate() >> RmaNumber.of("ZWR-00001") }
    DomainEventPublisher eventPublisher = Mock()
    CreateReturnRequestService service = new CreateReturnRequestService(repository, numberGenerator, eventPublisher)

    def "happy path: valid command creates return and publishes event"() {
        given:
        repository.existsByOrderId("ORDER-001", "NEOPAK") >> false
        def command = validCommand()

        when:
        def result = service.create(command)

        then:
        result == RmaNumber.of("ZWR-00001")
        1 * repository.save({ ReturnRequest rma ->
            rma.rmaNumber() == RmaNumber.of("ZWR-00001")
            rma.status() == ReturnStatus.PENDING_SHIPMENT
        })
        1 * eventPublisher.publish(_ as ReturnRequestCreated)
    }

    def "duplicate order reference throws DuplicateReturnException"() {
        given:
        repository.existsByOrderId("ORDER-001", "NEOPAK") >> true

        when:
        service.create(validCommand())

        then:
        thrown(DuplicateReturnException)
        0 * repository.save(_)
        0 * eventPublisher.publish(_)
    }

    def "generated rma number is returned"() {
        given:
        repository.existsByOrderId(*_) >> false

        when:
        def result = service.create(validCommand())

        then:
        result == RmaNumber.of("ZWR-00001")
    }

    private CreateReturnRequestCommand validCommand() {
        new CreateReturnRequestCommand(
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )
    }
}
