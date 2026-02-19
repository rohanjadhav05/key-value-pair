package com.kvstore.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

final class NodeManager {

    private final ConsistentHashRing<CacheNode> ring;

    NodeManager(int virtualNodes) {
        this.ring = new ConsistentHashRing<>(virtualNodes);
    }

    void addNode(CacheNode node) {
        Objects.requireNonNull(node, "node");
        ring.addNode(node);
    }

    void addNodes(Collection<CacheNode> nodes) {
        for (CacheNode node : nodes) {
            addNode(node);
        }
    }

    void removeNode(CacheNode node) {
        Objects.requireNonNull(node, "node");
        ring.removeNode(node);
    }

    List<CacheNode> nodesForKey(String key, int maxCount) {
        return new ArrayList<>(ring.getNodesForKey(key, maxCount));
    }
}

