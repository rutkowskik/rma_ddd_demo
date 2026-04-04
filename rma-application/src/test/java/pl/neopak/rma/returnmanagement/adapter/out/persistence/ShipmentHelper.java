package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.neopak.rma.returnmanagement.domain.model.PackageDimensions;
import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;

import java.util.UUID;

/**
 * Pomocniczy bean testowy pozwalajacy na bezposrednia manipulacje danymi
 * w bazie danych bez angarzowania warstwy JPA i mechanizmu optymistycznego blokowania.
 * Uzywany w testach E2E do przygotowania stanu agregatu pomiedzy krokami scenariusza.
 */
@TestComponent
public class ShipmentHelper {

    private final JdbcTemplate jdbcTemplate;

    public ShipmentHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void addShipment(String rmaNumber, PackageDimensions dimensions) {
        String returnRequestId = jdbcTemplate.queryForObject(
                "SELECT id FROM return_management.return_requests WHERE rma_number = ?",
                String.class,
                rmaNumber
        );

        jdbcTemplate.update(
                "INSERT INTO return_management.shipments " +
                "(id, return_request_id, weight_kg, length_cm, width_cm, height_cm, label_url, tracking_number, received) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, NULL, NULL, false)",
                UUID.randomUUID().toString(),
                returnRequestId,
                dimensions.getWeightKg(),
                dimensions.getLengthCm(),
                dimensions.getWidthCm(),
                dimensions.getHeightCm()
        );
    }

    @Transactional
    public void startVerification(String rmaNumber) {
        int updated = jdbcTemplate.update(
                "UPDATE return_management.return_requests SET status = ?, version = version + 1 " +
                "WHERE rma_number = ? AND status = ?",
                ReturnStatus.VERIFICATION.name(),
                rmaNumber,
                ReturnStatus.RECEIVED.name()
        );
        if (updated == 0) {
            throw new IllegalStateException(
                    "Could not transition to VERIFICATION for RMA: " + rmaNumber +
                    " (expected RECEIVED status)");
        }
    }
}
