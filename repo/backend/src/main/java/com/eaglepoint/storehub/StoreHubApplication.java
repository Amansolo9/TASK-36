package com.eaglepoint.storehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StoreHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(StoreHubApplication.class, args);
    }
}
