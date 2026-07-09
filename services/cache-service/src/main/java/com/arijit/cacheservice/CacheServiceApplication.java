package com.arijit.cacheservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CacheServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CacheServiceApplication.class, args);
    }
}
