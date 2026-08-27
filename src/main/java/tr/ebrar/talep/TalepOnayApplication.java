package tr.ebrar.talep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TalepOnayApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalepOnayApplication.class, args);
    }
}
