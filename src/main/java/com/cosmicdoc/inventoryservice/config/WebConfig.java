package com.cosmicdoc.inventoryservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                    "http://localhost:4200",  // Default Angular port
                    "http://localhost:4201",  // Alternative Angular port
                    "http://localhost:3000",  // Common React port
                    "http://localhost:8080",  // Common development port
                    "http://127.0.0.1:4200", // Using IP address instead of localhost
                    "http://127.0.0.1:8080",  // Using IP address instead of localhost
                    "https://healthcare-app-1078740886343.us-central1.run.app", // Healthcare app URL
                    "https://healthcare-app-145837205370.asia-south1.run.app", // Healthcare app URL
                    "https://healthcare-app-191932434541.asia-south1.run.app" // Healthcare app URL
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600); // 1 hour cache for preflight requests
    }
}
