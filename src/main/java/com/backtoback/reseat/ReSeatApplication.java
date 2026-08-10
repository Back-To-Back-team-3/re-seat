package com.backtoback.reseat;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

@EnableScheduling
@SpringBootApplication
public class ReSeatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReSeatApplication.class, args);
    }

    public static void main(String[] args) {
        SpringApplication.run(ReSeatApplication.class, args);
    }

    @PostConstruct
    public void init() {

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

}
