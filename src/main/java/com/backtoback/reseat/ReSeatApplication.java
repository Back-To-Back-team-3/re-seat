package com.backtoback.reseat;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
public class ReSeatApplication {


    public static void main(String[] args) {
        SpringApplication.run(ReSeatApplication.class, args);
    }

    @PostConstruct
    public void init() {

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

}
