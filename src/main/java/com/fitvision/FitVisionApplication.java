package com.fitvision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FitVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitVisionApplication.class, args);
    }
}
