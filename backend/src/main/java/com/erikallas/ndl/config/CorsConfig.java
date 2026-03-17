package com.erikallas.ndl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
// @Profile("local")
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins(
                        // HTTP (local development)
                        "http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173",
                        "http://127.0.0.1:5174",
                        // HTTPS (local development with self-signed cert)
                        "https://localhost:5173", "https://localhost:5174", "https://127.0.0.1:5173",
                        "https://127.0.0.1:5174").allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Authorization", "Content-Type").allowCredentials(true).maxAge(3600);
            }
        };
    }
}
