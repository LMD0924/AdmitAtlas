package com.admitatlas.plan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.admitatlas.plan", "com.admitatlas.common"})
public class PlanReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanReportApplication.class, args);
    }
}
