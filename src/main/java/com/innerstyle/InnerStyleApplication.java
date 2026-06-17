package com.innerstyle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>Component scanning covers everything under {@code com.innerstyle}, so the Meshy
 * module (controller / service / repository / client) is wired automatically. Scheduling
 * is enabled for the polling fallback that reconciles Meshy task status.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class InnerStyleApplication {

    public static void main(String[] args) {
        SpringApplication.run(InnerStyleApplication.class, args);
    }
}
