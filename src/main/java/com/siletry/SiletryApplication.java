package com.siletry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class SiletryApplication {

    public static void main(String[] args) {
        // All clinics are in India for now. Everything is stored and computed in IST.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(SiletryApplication.class, args);
    }
}
