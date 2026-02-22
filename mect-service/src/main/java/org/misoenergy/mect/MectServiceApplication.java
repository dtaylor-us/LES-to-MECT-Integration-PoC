package org.misoenergy.mect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MectServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MectServiceApplication.class, args);
    }
}
