package pl.neopak.rma.config;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Kept for @Import(TestStubsConfig.class) compatibility in existing @SpringBootTest specs.
 * All ports now have production implementations — no stubs needed.
 */
@TestConfiguration
public class TestStubsConfig {
}
