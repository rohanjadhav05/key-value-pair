package com.kvstore.client;

import java.net.URI;
import java.util.Objects;

public final class CacheNode {
    private final URI baseUri;

    public CacheNode(String baseUrl) {
        this(URI.create(baseUrl));
    }

    public CacheNode(URI baseUri) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
    }

    public URI getBaseUri() {
        return baseUri;
    }

    public URI resolve(String path) {
        return baseUri.resolve(path);
    }

    @Override
    public String toString() {
        return baseUri.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheNode)) return false;
        CacheNode cacheNode = (CacheNode) o;
        return baseUri.equals(cacheNode.baseUri);
    }

    @Override
    public int hashCode() {
        return baseUri.hashCode();
    }
}

