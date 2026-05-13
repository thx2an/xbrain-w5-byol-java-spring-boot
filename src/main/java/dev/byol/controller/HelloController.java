package dev.byol.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
            "ok", true,
            "runtime", "spring-boot",
            "message", "hello from your existing app"
        );
    }

    @GetMapping("/api/hello/{name}")
    public Map<String, Object> hello(@PathVariable String name) {
        return Map.of(
            "greeting", "Hello, " + name + "!",
            "timestamp", Instant.now().toString()
        );
    }

    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        return Map.of("echo", body);
    }
}
