## Distributed Key–Value Store (Memcache‑like)

This project is a **Java 17+, Maven‑based distributed in‑memory key–value store**, inspired by Memcache.

It provides:

- **Core LRU cache library** (`kv-core`)
- **HTTP server node** (`kv-server`) exposing `PUT`/`GET` operations
- **Client SDK** (`kv-client`) that does **consistent hashing** + simple retry
- **Examples module** (`examples`) showing how to start multiple nodes and use the client

The system is designed for **O(1)** average access, **LRU eviction**, and **horizontal scalability** by sharding keys across nodes.

---

## Project Structure

```text
kv-store/
 ├── pom.xml                 # Parent multi‑module POM
 ├── kv-core/                # Core LRU cache library
 ├── kv-server/              # Spring Boot HTTP node using kv-core
 ├── kv-client/              # Java client SDK with consistent hashing
 └── examples/               # Example that starts nodes & does put/get
```

### `kv-core` – LRU Cache Library

- `Cache<K,V>` – simple cache abstraction:
  - `void put(K key, V value)`
  - `Optional<V> get(K key)`
  - `int size()`
  - `int capacity()`
- `LRUCache<K,V>` – **thread‑safe LRU cache** using:
  - `HashMap<K, Node>` for O(1) lookup
  - Doubly‑linked list for O(1) LRU updates and evictions
  - `ReentrantReadWriteLock` for concurrency
- `EvictionListener<K,V>` – optional hook when entries are evicted.

### `kv-server` – Node HTTP Server

- Spring Boot application: `com.kvstore.server.KvServerApplication`
- Cache bean:
  - Configured in `CacheConfig` as `Cache<String,String>` using `LRUCache`.
  - Capacity controlled by property `kv.cache.capacity` (default 1000).
- REST endpoints (in `CacheController`):
  - `POST /put` – body: `{"key":"k","value":"v"}`; stores a value.
  - `GET /get/{key}` – returns the stored value or `404` if not found.
  - `GET /health` – simple health check.

### `kv-client` – Client SDK with Consistent Hashing

Key classes:

- `ConsistentHashRing<T>` – generic **consistent hash ring** with virtual nodes:
  - Spreads keys across nodes using MD5‑based hashing.
  - Supports:
    - `addNode(T node)`
    - `removeNode(T node)`
    - `getNodeForKey(String key)`
    - `getNodesForKey(String key, int count)` for retries.
- `CacheNode` – wraps a node base URI (e.g. `http://localhost:8081`).
- `NodeManager` – thin wrapper around `ConsistentHashRing<CacheNode>`.
- `KeyValueClient` – public client:
  - Constructor:
    - `KeyValueClient(List<String> baseUrls)`
    - `KeyValueClient(List<String> baseUrls, int maxRetries)`
  - Methods:
    - `void put(String key, String value)`
    - `Optional<String> get(String key)`
  - Uses Java `HttpClient` + Jackson JSON under the hood.
  - For a given key:
    - Chooses the **primary node** from the hash ring.
    - On failure, **retries** on alternative nodes (up to `maxRetries` distinct nodes).

### `examples` – Example Usage

`com.kvstore.examples.ExampleMain`:

- Programmatically starts **two `kv-server` nodes** in‑process using Spring Boot:
  - Node 1: port `8081`, capacity `500`
  - Node 2: port `8082`, capacity `500`
- Creates a `KeyValueClient` with both nodes.
- Performs:
  - `put("key1", "value1")`
  - `get("key1")` and prints the result.

---

## How the LRU Cache Works (Core Design)

The LRU cache (`LRUCache<K,V>`) guarantees **O(1)** average time for `put` and `get` by combining:

- **HashMap**: maps key → node pointer.
- **Doubly‑linked list**: tracks usage order (most‑recently used at the head).

Operations:

- **get(key)**:
  - Look up `Node` in the map.
  - If found, move that node to the **head** of the list (mark as most recently used).
  - Return the value.
- **put(key,value)**:
  - If key exists:
    - Update the value.
    - Move node to head.
  - If key is new:
    - Create new node and insert at head.
    - If size exceeds `capacity`:
      - Remove the **tail** node (least‑recently used entry).
      - Invoke `EvictionListener` if provided.

Concurrency:

- Uses `ReentrantReadWriteLock`:
  - **Write lock** for `put` and `get` (because each `get` also mutates the LRU list).
  - **Read lock** for `size()`.

---

## How Consistent Hashing Works

Consistent hashing is used to map keys to nodes while minimizing reshuffling when nodes are added/removed.

- Each `CacheNode` is represented by multiple **virtual nodes** on a hash ring:
  - For each physical node, the ring adds `virtualNodes` entries based on `hash(node.toString() + "#" + i)`.
  - This smooths out distribution and avoids hotspots.
- To locate a node for a key:
  - Compute `hash(key)` and look up the **first node clockwise** on the ring (or wrap around to the first entry).
- `KeyValueClient`:
  - Uses `ConsistentHashRing<CacheNode>` via `NodeManager`.
  - For each key, obtains a **list of candidate nodes** (for retry).
  - First candidate is the primary, subsequent ones are fallback nodes for retry if needed.

Benefits:

- **Horizontal scalability**: add new nodes to handle more keys without global rehash.
- **Partition tolerance**: if a node fails, client retries on another candidate node.

---

## Building the Project

Prerequisites:

- Java **17+**
- Maven **3.8+**

From the project root:

```bash
mvn clean package
```

This builds all modules and produces:

- `kv-core/target/kv-core-0.1.0-SNAPSHOT.jar`
- `kv-server/target/kv-server-0.1.0-SNAPSHOT.jar`
- `kv-client/target/kv-client-0.1.0-SNAPSHOT.jar`
- `examples/target/examples-0.1.0-SNAPSHOT.jar` (non‑executable; used for running via `mvn exec` or IDE)

---

## Running a Single Server Node

After building, from the repo root:

```bash
java -jar kv-server/target/kv-server-0.1.0-SNAPSHOT.jar \
  --server.port=8081 \
  --kv.cache.capacity=500
```

- `server.port` sets the HTTP port.
- `kv.cache.capacity` configures the **max entries** for the LRU cache on that node.

Once running, you can interact using `curl`:

### Put a Value

```bash
curl -X POST http://localhost:8081/put \
  -H "Content-Type: application/json" \
  -d '{"key":"user:1","value":"alice"}'
```

### Get a Value

```bash
curl http://localhost:8081/get/user:1
```

### Health Check

```bash
curl http://localhost:8081/health
```

---

## Running Multiple Nodes (Distributed Setup)

Start **two separate terminals** and run:

### Terminal 1 – Node 1

```bash
java -jar kv-server/target/kv-server-0.1.0-SNAPSHOT.jar \
  --server.port=8081 \
  --kv.cache.capacity=500
```

### Terminal 2 – Node 2

```bash
java -jar kv-server/target/kv-server-0.1.0-SNAPSHOT.jar \
  --server.port=8082 \
  --kv.cache.capacity=500
```

You now have two independent cache nodes, each with its own in‑memory LRU store.

Keys will later be **sharded across these nodes** using the client’s consistent hashing.

---

## Using the Java Client (KeyValueClient)

Add a dependency on `kv-client` (if using from another Maven project):

```xml
<dependency>
    <groupId>com.kvstore</groupId>
    <artifactId>kv-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Basic usage:

```java
import com.kvstore.client.KeyValueClient;

import java.util.Arrays;
import java.util.Optional;

public class ClientExample {
    public static void main(String[] args) throws Exception {
        KeyValueClient client = new KeyValueClient(
                Arrays.asList("http://localhost:8081", "http://localhost:8082"),
                3 // maxRetries
        );

        client.put("session:123", "data-for-session");
        Optional<String> value = client.get("session:123");
        System.out.println("Got: " + value.orElse("<not-found>"));
    }
}
```

What happens for `"session:123"`:

- `KeyValueClient`:
  - Uses consistent hashing to pick a primary node (e.g. `http://localhost:8082`).
  - Sends `POST /put` to that node.
  - On `get`, it again calculates the same node from the ring.
  - If that node is down, it retries on alternative nodes (if available).

---

## Running the Example Main

The `examples` module contains `ExampleMain` which:

- Starts **two embedded server nodes in‑process** (no separate `java -jar` needed).
- Uses `KeyValueClient` to demonstrate a simple put/get flow.

From the project root:

```bash
mvn -pl examples -q exec:java -Dexec.mainClass=com.kvstore.examples.ExampleMain
```

You should see output similar to:

```text
Putting key1=value1
Got for key1: value1
```

This shows:

- Nodes booted successfully.
- Client applied consistent hashing and stored/fetched the value across the mini‑cluster.

---

## How This Mimics Memcache‑Style Deployment

- **In‑memory store per node**:
  - Each `kv-server` instance keeps data in RAM only (like Memcache).
  - State is **not shared** between nodes; distribution is client‑side.
- **Sharding via consistent hashing**:
  - Client decides which node a key goes to.
  - Adding/removing nodes only reshuffles a subset of keys.
- **LRU eviction per node**:
  - When a node’s capacity is reached, least‑recently used entries are evicted.
  - Eviction can trigger custom logic via `EvictionListener` in `kv-core` (if wired).
- **Horizontal scaling**:
  - To scale, deploy more `kv-server` nodes and point clients at all of them.
  - Consistent hashing rebalances keys without centralized coordination.

This architecture gives you a **lightweight, pluggable Memcache‑like cache tier** suitable for demos, local experimentation, or as a starting point for more advanced features (replication, persistence, etc.).

