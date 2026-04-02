package pl.neopak.rma.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import pl.neopak.rma.returnmanagement.domain.model.PackageDimensions;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.out.*;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@TestConfiguration
public class TestStubsConfig {

    @Bean
    public DomainEventPublisher domainEventPublisher() {
        return event -> {};
    }

    @Bean
    public RmaNumberGenerator rmaNumberGenerator() {
        AtomicInteger seq = new AtomicInteger(9000);
        return () -> RmaNumber.of(String.format("ZWR-%05d", seq.incrementAndGet()));
    }

    @Bean
    public SlaClockPort slaClockPort() {
        return Instant::now;
    }

    @Bean
    public PhotoStoragePort photoStoragePort() {
        return new PhotoStoragePort() {
            @Override
            public String store(byte[] photoBytes, String rmaNumber, String filename) {
                return "http://photos.test/" + rmaNumber + "/" + filename;
            }

            @Override
            public void delete(String url) {}
        };
    }

    @Bean
    public ShipmentTrackingRepository shipmentTrackingRepository() {
        return trackingNumber -> Optional.empty();
    }

    @Bean
    public CourierGateway courierGateway() {
        return new CourierGateway() {
            @Override
            public String createShipment(PackageDimensions dimensions, String rmaNumber) {
                return "http://courier.test/labels/" + rmaNumber + ".pdf";
            }

            @Override
            public byte[] getLabel(String shipmentId) {
                return new byte[0];
            }
        };
    }
}
