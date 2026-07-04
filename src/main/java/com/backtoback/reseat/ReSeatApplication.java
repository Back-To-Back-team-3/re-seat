package com.backtoback.reseat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class ReSeatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReSeatApplication.class, args);
    }

}
