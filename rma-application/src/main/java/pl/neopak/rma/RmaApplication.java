package pl.neopak.rma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.neopak.rma.courier.adapter.out.DpdProperties;
import pl.neopak.rma.courier.adapter.out.InPostProperties;
import pl.neopak.rma.payment.adapter.out.PayUProperties;

@SpringBootApplication
@EnableConfigurationProperties({PayUProperties.class, InPostProperties.class, DpdProperties.class})
public class RmaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RmaApplication.class, args);
    }
}
