package pl.neopak.rma.returnmanagement.adapter.out.rma;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.out.RmaNumberGenerator;

@Component
public class DatabaseRmaNumberGenerator implements RmaNumberGenerator {

    private final JdbcTemplate jdbc;

    public DatabaseRmaNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RmaNumber generate() {
        Long next = jdbc.queryForObject(
                "SELECT nextval('return_management.rma_number_seq')", Long.class);
        return RmaNumber.of(String.format("ZWR-%05d", next));
    }
}
