package com.backtoback.reseat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ReSeatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReSeatApplication.class, args);
    }

}
