package pl.neopak.rma.returnmanagement.service

import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException
import pl.neopak.rma.returnmanagement.domain.model.CustomerInfo
import pl.neopak.rma.returnmanagement.domain.model.OrderReference
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber
import pl.neopak.rma.returnmanagement.domain.model.SourceSystem
import pl.neopak.rma.returnmanagement.port.out.DomainEventPublisher
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository
import spock.lang.Specification

class ConfirmPaymentServiceSpec extends Specification {

    ReturnRequestRepository repository = Mock()
    DomainEventPublisher eventPublisher = Mock()
    ConfirmPaymentService service = new ConfirmPaymentService(repository, eventPublisher)

    def "confirms payment on existing RMA"() {
        given:
        def rma = aReturnRequest()
        repository.findByRmaNumber(RmaNumber.of("ZWR-00001")) >> Optional.of(rma)

        when:
        service.confirmPayment("ZWR-00001", "PAY-SESSION-123")

        then:
        rma.isPaymentConfirmed()
        1 * repository.save(rma)
        1 * eventPublisher.publish(_)
    }

    def "throws RmaNotFoundException for unknown rmaNumber"() {
        given:
        repository.findByRmaNumber(_) >> Optional.empty()

        when:
        service.confirmPayment("ZWR-99999", "PAY-123")

        then:
        thrown(RmaNotFoundException)
        0 * repository.save(_)
    }

    def "idempotency: confirming payment twice does not throw"() {
        given:
        def rma = aReturnRequest()
        rma.confirmPayment("FIRST")
        repository.findByRmaNumber(_) >> Optional.of(rma)

        when:
        service.confirmPayment("ZWR-00001", "SECOND")

        then:
        noExceptionThrown()
    }

    private ReturnRequest aReturnRequest() {
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        )
        rma.pullEvents()
        rma
    }
}
