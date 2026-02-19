package com.kvstore.core;

@FunctionalInterface
public interface EvictionListener<K, V> {
    void onEvict(K key, V value);
}

