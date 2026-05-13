package dev.byol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The existing Spring Boot app. `mvn spring-boot:run` (or run main directly)
 * boots a local Tomcat on http://localhost:8080. Lambda-unaware.
 */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
