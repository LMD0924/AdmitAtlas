package com.admitatlas.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.admitatlas.match", "com.admitatlas.common"})
public class MatchEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchEngineApplication.class, args);
    }
}
