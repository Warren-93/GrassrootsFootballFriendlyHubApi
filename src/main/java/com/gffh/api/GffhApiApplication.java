package com.gffh.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GffhApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(GffhApiApplication.class, args);
    }
}
