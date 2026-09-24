package com.tawsif.rescuelab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RescueLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(RescueLabApplication.class, args);
    }
}

