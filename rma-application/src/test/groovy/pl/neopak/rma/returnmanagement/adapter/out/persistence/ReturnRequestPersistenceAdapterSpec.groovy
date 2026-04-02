package pl.neopak.rma.returnmanagement.adapter.out.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import pl.neopak.rma.config.TestStubsConfig
import pl.neopak.rma.returnmanagement.domain.model.*
import spock.lang.Specification

import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestStubsConfig)
@Transactional
class ReturnRequestPersistenceAdapterSpec extends Specification {

    @Autowired
    ReturnRequestPersistenceAdapter adapter

    def "should save and find return request by rma number"() {
        given:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORD-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@test.pl", "Jan Kowalski")
        )
        rma.addLineItem("PROD-1", 2, ReturnReason.DAMAGED)
        rma.pullEvents()

        when:
        adapter.save(rma)
        def found = adapter.findByRmaNumber(RmaNumber.of("ZWR-00001"))

        then:
        found.isPresent()
        found.get().rmaNumber().value() == "ZWR-00001"
        found.get().status() == ReturnStatus.PENDING_SHIPMENT
        found.get().orderReference().orderId() == "ORD-001"
        found.get().customerInfo().email() == "jan@test.pl"
        found.get().lineItems().size() == 1
        found.get().lineItems()[0].getProductId() == "PROD-1"
        found.get().lineItems()[0].getQuantity() == 2
    }

    def "should save and reload shipments"() {
        given:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00002"),
            new OrderReference("ORD-002", SourceSystem.NEOPAK),
            CustomerInfo.of("anna@test.pl", "Anna Nowak")
        )
        rma.addShipment(new PackageDimensions(5, 40, 30, 20))
        rma.addShipment(new PackageDimensions(10, 60, 50, 40))
        rma.pullEvents()

        when:
        adapter.save(rma)
        def found = adapter.findByRmaNumber(RmaNumber.of("ZWR-00002")).get()

        then:
        found.shipments().size() == 2
        found.shipments()[0].getDimensions().getWeightKg() == 5
        found.shipments()[1].getDimensions().getWeightKg() == 10
    }

    def "should return empty when rma number not found"() {
        when:
        def found = adapter.findByRmaNumber(RmaNumber.of("ZWR-99999"))

        then:
        found.isEmpty()
    }

    def "should detect duplicate order by orderId and sourceSystem"() {
        given:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00003"),
            new OrderReference("ORD-DUP", SourceSystem.NEOPAK),
            CustomerInfo.of("dup@test.pl", "Dup User")
        )
        rma.pullEvents()
        adapter.save(rma)

        expect:
        adapter.existsByOrderId("ORD-DUP", "NEOPAK") == true
        adapter.existsByOrderId("ORD-DUP", "ALLEGRO") == false
        adapter.existsByOrderId("ORD-OTHER", "NEOPAK") == false
    }

    def "should persist state changes across save calls"() {
        given:
        def rma = ReturnRequest.create(
            RmaNumber.of("ZWR-00004"),
            new OrderReference("ORD-004", SourceSystem.NEOPAK),
            CustomerInfo.of("test@test.pl", "Test User")
        )
        rma.addShipment(new PackageDimensions(3, 30, 20, 10))
        rma.pullEvents()
        adapter.save(rma)

        when:
        def loaded = adapter.findByRmaNumber(RmaNumber.of("ZWR-00004")).get()
        loaded.confirmPayment("sess-abc")
        loaded.assignLabels(["http://label.pdf"])
        loaded.pullEvents()
        adapter.save(loaded)

        def reloaded = adapter.findByRmaNumber(RmaNumber.of("ZWR-00004")).get()

        then:
        reloaded.status() == ReturnStatus.IN_TRANSIT
        reloaded.isPaymentConfirmed() == true
        reloaded.shipments()[0].getLabelUrl() == "http://label.pdf"
    }

    def "should find by multiple statuses"() {
        given:
        def rma1 = ReturnRequest.create(
            RmaNumber.of("ZWR-00005"),
            new OrderReference("ORD-005", SourceSystem.NEOPAK),
            CustomerInfo.of("a@test.pl", "A B")
        )
        def rma2 = ReturnRequest.create(
            RmaNumber.of("ZWR-00006"),
            new OrderReference("ORD-006", SourceSystem.NEOPAK),
            CustomerInfo.of("b@test.pl", "B C")
        )
        [rma1, rma2].each { it.pullEvents(); adapter.save(it) }

        when:
        def results = adapter.findByStatuses([ReturnStatus.PENDING_SHIPMENT])

        then:
        results.size() >= 2
        results.every { it.status() == ReturnStatus.PENDING_SHIPMENT }
    }
}
