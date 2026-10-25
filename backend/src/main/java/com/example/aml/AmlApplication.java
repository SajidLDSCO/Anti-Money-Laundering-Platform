package com.example.aml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync // notification emails are sent in the background
public class AmlApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmlApplication.class, args);
    }
}
