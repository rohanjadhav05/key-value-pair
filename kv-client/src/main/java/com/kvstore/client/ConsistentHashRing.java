package com.kvstore.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Simple consistent hash ring with virtual nodes for even distribution.
 */
final class ConsistentHashRing<T> {

    private final int virtualNodes;
    private final NavigableMap<Long, T> ring = new TreeMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    ConsistentHashRing(int virtualNodes) {
        if (virtualNodes <= 0) {
            throw new IllegalArgumentException("virtualNodes must be > 0");
        }
        this.virtualNodes = virtualNodes;
    }

    void addNode(T node) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < virtualNodes; i++) {
                long hash = hash(node.toString() + "#" + i);
                ring.put(hash, node);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    void removeNode(T node) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < virtualNodes; i++) {
                long hash = hash(node.toString() + "#" + i);
                ring.remove(hash);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    Optional<T> getNodeForKey(String key) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return Optional.empty();
            }
            long hash = hash(key);
            Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                entry = ring.firstEntry();
            }
            return Optional.ofNullable(entry.getValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns up to {@code count} distinct nodes to be used for retries.
     */
    List<T> getNodesForKey(String key, int count) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty() || count <= 0) {
                return List.of();
            }
            long hash = hash(key);
            List<T> result = new ArrayList<>(count);
            Set<T> seen = new LinkedHashSet<>();

            NavigableMap<Long, T> tail = ring.tailMap(hash, true);
            for (T node : tail.values()) {
                if (seen.add(node)) {
                    result.add(node);
                    if (result.size() >= count) {
                        return result;
                    }
                }
            }
            for (T node : ring.values()) {
                if (seen.add(node)) {
                    result.add(node);
                    if (result.size() >= count) {
                        break;
                    }
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Use first 8 bytes as a long
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xffL);
            }
            return h & 0x7fffffffffffffffL;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to String.hashCode if MD5 unavailable
            return (long) key.hashCode() & 0x7fffffffL;
        }
    }
}

