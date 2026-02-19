package com.kvstore.server.web;

import com.kvstore.core.Cache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class CacheController {

    private final Cache<String, String> cache;

    public CacheController(Cache<String, String> cache) {
        this.cache = cache;
    }

    @PostMapping("/put")
    public ResponseEntity<Void> put(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || value == null) {
            return ResponseEntity.badRequest().build();
        }
        cache.put(key, value);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/get/{key}")
    public ResponseEntity<String> get(@PathVariable("key") String key) {
        return cache.get(key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}

