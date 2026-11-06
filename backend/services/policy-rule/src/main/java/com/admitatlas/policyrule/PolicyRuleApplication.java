package com.admitatlas.policyrule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.admitatlas.policyrule", "com.admitatlas.common"})
public class PolicyRuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyRuleApplication.class, args);
    }
}
