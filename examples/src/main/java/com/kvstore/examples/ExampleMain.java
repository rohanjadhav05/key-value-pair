package com.kvstore.examples;

import com.kvstore.client.KeyValueClient;
import com.kvstore.server.KvServerApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public final class ExampleMain {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext node1 = startNode(8081, 2);
        ConfigurableApplicationContext node2 = startNode(8082, 2);

        try {
            KeyValueClient client = new KeyValueClient(
                    Arrays.asList("http://localhost:8081", "http://localhost:8082"),
                    3
            );

            System.out.println("============================");
            System.out.println("Putting key1=value1");
            client.put("key1", "value1");
            client.put("key2", "value2");
            client.put("key3", "value3");
            client.put("key4", "value4");
            Optional<String> value1 = client.get("key1");
            Optional<String> value2 = client.get("key2");
            Optional<String> value3 = client.get("key3");
            Optional<String> value4 = client.get("key4");

            System.out.println("Got for key1: " + value1.orElse("<null>"));
            System.out.println("Got for key2: " + value2.orElse("<null>"));
            System.out.println("Got for key3: " + value3.orElse("<null>"));
            System.out.println("Got for key4: " + value4.orElse("<null>"));
        } finally {
            closeQuietly(node1);
            closeQuietly(node2);
        }
    }

    private static ConfigurableApplicationContext startNode(int port, int capacity) {
        return new SpringApplicationBuilder(KvServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=" + port,
                        "kv.cache.capacity=" + capacity
                )
                .run();
    }

    private static void closeQuietly(ConfigurableApplicationContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception ignored) {
            }
        }
    }
}

