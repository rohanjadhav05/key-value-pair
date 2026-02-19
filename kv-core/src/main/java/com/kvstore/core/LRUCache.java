package com.kvstore.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe LRU cache with O(1) average-time get/put.
 * Uses a HashMap + doubly linked list for eviction ordering.
 */
public final class LRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final EvictionListener<K, V> evictionListener;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<K, Node<K, V>> index;
    private Node<K, V> head;
    private Node<K, V> tail;

    public LRUCache(int capacity) {
        this(capacity, null);
    }

    public LRUCache(int capacity, EvictionListener<K, V> evictionListener) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.evictionListener = evictionListener;
        this.index = new HashMap<>(Math.min(capacity, 1024));
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        lock.writeLock().lock();
        try {
            Node<K, V> existing = index.get(key);
            if (existing != null) {
                existing.value = value;
                moveToHead(existing);
                return;
            }

            Node<K, V> node = new Node<>(key, value);
            index.put(key, node);
            addToHead(node);

            if (index.size() > capacity) {
                Node<K, V> evicted = removeTail();
                if (evicted != null) {
                    index.remove(evicted.key);
                    if (evictionListener != null) {
                        evictionListener.onEvict(evicted.key, evicted.value);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key");

        lock.writeLock().lock();
        try {
            Node<K, V> node = index.get(key);
            if (node == null) {
                return Optional.empty();
            }
            moveToHead(node);
            return Optional.of(node.value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return index.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    private void addToHead(Node<K, V> node) {
        node.prev = null;
        node.next = head;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = node;
        }
    }

    private void moveToHead(Node<K, V> node) {
        if (node == head) {
            return;
        }
        removeNode(node);
        addToHead(node);
    }

    private void removeNode(Node<K, V> node) {
        Node<K, V> prev = node.prev;
        Node<K, V> next = node.next;

        if (prev != null) {
            prev.next = next;
        } else {
            head = next;
        }

        if (next != null) {
            next.prev = prev;
        } else {
            tail = prev;
        }

        node.prev = null;
        node.next = null;
    }

    private Node<K, V> removeTail() {
        if (tail == null) {
            return null;
        }
        Node<K, V> removed = tail;
        removeNode(removed);
        return removed;
    }

    private static final class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> prev;
        private Node<K, V> next;

        private Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}

