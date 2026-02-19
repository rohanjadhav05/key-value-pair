package com.kvstore.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Client SDK routing requests to cache nodes using consistent hashing.
 */
public final class KeyValueClient {

    private final NodeManager nodeManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public KeyValueClient(List<String> baseUrls) {
        this(baseUrls, 3);
    }

    public KeyValueClient(List<String> baseUrls, int maxRetries) {
        if (baseUrls == null || baseUrls.isEmpty()) {
            throw new IllegalArgumentException("At least one node URL is required");
        }
        this.maxRetries = Math.max(1, maxRetries);
        this.nodeManager = new NodeManager(128);
        List<CacheNode> nodes = new ArrayList<>();
        for (String url : baseUrls) {
            nodes.add(new CacheNode(url));
        }
        this.nodeManager.addNodes(nodes);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void put(String key, String value) throws IOException, InterruptedException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        List<CacheNode> candidates = nodeManager.nodesForKey(key, maxRetries);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No cache nodes available");
        }

        Map<String, String> body = Map.of("key", key, "value", value);
        byte[] json = objectMapper.writeValueAsBytes(body);

        IOException lastException = null;
        for (CacheNode node : candidates) {
            System.out.println("Putting key : "+key+" in node : "+node.toString());
            URI uri = node.resolve("/put");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                    .build();
            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return;
                }
            } catch (IOException e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("PUT failed on all nodes");
    }

    public Optional<String> get(String key) throws IOException, InterruptedException {
        Objects.requireNonNull(key, "key");
        List<CacheNode> candidates = nodeManager.nodesForKey(key, maxRetries);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No cache nodes available");
        }

        IOException lastException = null;
        for (CacheNode node : candidates) {
            System.out.println("Getting key : "+key+" from server : "+node.toString());
            URI uri = node.resolve("/get/" + key);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return Optional.ofNullable(response.body());
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                }
            } catch (IOException e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        return Optional.empty();
    }
}

