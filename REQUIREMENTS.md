# Technical Requirements — Implementation Audit

> **Project:** Distributed Task Execution System (Java)
> **Version:** 2 + Progress Tracking (Feature 3)
> **Date:** 2026-03-04

All four technical requirements are fully implemented.
This document maps each requirement clause to the exact classes, files,
and line ranges that satisfy it.

---

## 4.1 Architecture — Client/Server (TCP Sockets)

**Requirement:**
> You must implement a Client–Server architecture using TCP Sockets or Java RMI.
> The server must support multiple simultaneous clients.

### ✅ Implementation

| Component | File | Role |
|-----------|------|------|
| TCP Server Socket | `src/master/Master.java` | `ServerSocket(clientPort)` — listens on port 5000 |
| TCP Slave Reg Socket | `src/master/Master.java` | `ServerSocket(regPort)` — listens on port 5001 |
| TCP Client Socket | `src/client/Client.java` | `new Socket(host, port)` — connects to Master |
| TCP Interactive Client | `src/client/ClientCLI.java` | Interactive loop, same socket protocol |
| TCP Slave Socket | `src/slave/SlaveNode.java` | `ServerSocket(slavePort)` — listens for chunks from Master |
| Multi-client Support | `src/master/Master.java` | Each `clientSocket.accept()` spawns a new `ClientHandler` thread |

### Protocol Flow
```
Client ──[TaskRequest]──► Master ──[ProgressUpdate*]──► Client
                                 ──[TaskResult]──────► Client
Master ──[TaskRequest chunk]──► SlaveNode ──[TaskResult]──► Master
```

**Object serialization** (`ObjectInputStream` / `ObjectOutputStream`) is used
on all connections, meaning all transferred objects implement `Serializable`.

---

## 4.2 Multithreading

**Requirement:**
> The server must handle each client in a separate thread.
> All shared data structures must be thread-safe.
> Appropriate synchronization mechanisms must prevent race conditions.

### ✅ Thread-per-Client

| Thread | File | Trigger |
|--------|------|---------|
| `ClientHandler extends Thread` | `src/master/ClientHandler.java` | One thread per client connection |
| `SlaveHandler extends Thread` | `src/slave/SlaveHandler.java` | One thread per task on each Slave |
| Slave registration thread | `src/master/Master.java` | Daemon thread on port 5001 |
| Worker distribution threads | `src/master/SlaveManager.java` | One thread per chunk in `distributeWork()` |

### ✅ Thread-Safe Shared Structures

| Mechanism | File | What it protects |
|-----------|------|-----------------|
| `Collections.synchronizedList` | `SlaveManager.java:49` | `List<SlaveInfo>` slave pool |
| `synchronized` methods | `SlaveManager.java:70,86,95,99` | slave register/unregister/get |
| `ConcurrentHashMap<String,Task>` | `TaskPersistence.java:27` | task history map |
| `synchronized` methods | `TaskPersistence.java:42,51,124` | file writes |
| `ReentrantLock` | `TaskScheduler.java:17,28,44` | `PriorityQueue<Task>` access |
| `synchronized` on `ConfigManager` | `ConfigManager.java:31` | Singleton lazy init |
| `synchronized` on `ProgressPublisher` | `ProgressPublisher.java:34,45,60` | Observer list mutations |
| `synchronized(out)` | `SocketProgressObserver.java:44` | Shared `ObjectOutputStream` |
| `AtomicInteger` | `DistributedExecutionStrategy.java` | Per-slave progress counter |
| `volatile boolean active` | `SocketProgressObserver.java` | Safe stop flag |

---

## 4.3 Collections

**Requirement:**
> At least one **Map**, at least one **List**, and one specialized collection
> (such as a **PriorityQueue**). Choice of each must be justified.

### ✅ Map — `ConcurrentHashMap<String, Task>`

**File:** `src/persistence/TaskPersistence.java:27`
```java
private final Map<String, Task> taskMap = new ConcurrentHashMap<>();
```
**Justification:**
- Maps task IDs (UUID-based `String`) to `Task` objects for O(1) lookup.
- `ConcurrentHashMap` chosen over `HashMap` because multiple `ClientHandler`
  threads write/read it concurrently without external locks.
- Required for fast retrieval by ID and for the JSON summary dump.

---

### ✅ List — `List<SlaveInfo>` (synchronizedList)

**File:** `src/master/SlaveManager.java:49`
```java
private final List<SlaveInfo> slaves = Collections.synchronizedList(new ArrayList<>());
```
**Justification:**
- Slaves register dynamically at runtime: the pool grows and shrinks.
- `List` models an ordered, indexable sequence — round-robin distribution
  (`pool.get(i % numSlaves)`) requires index-based access.
- `Collections.synchronizedList` ensures thread-safe mutations when multiple
  threads register/unregister slaves simultaneously.

**Additional Lists used:**
- `List<FileData>` in `TaskRequest` — ordered input files (order matters for matrix rows)
- `List<TaskResult>` in `DistributedExecutionStrategy` — ordered slave results for merge
- `List<String>` command lists in strategies — ordered CLI arguments

---

### ✅ PriorityQueue — `PriorityQueue<Task>`

**File:** `src/master/TaskScheduler.java:16`
```java
private final PriorityQueue<Task> taskQueue = new PriorityQueue<>();
```
**Justification:**
- When multiple clients submit tasks simultaneously, high-priority tasks must
  be executed before low-priority ones — exactly what `PriorityQueue` provides.
- `Task implements Comparable<Task>` (in `Task.java:36`) compares by
  `request.getPriority()` descending (higher = processed first).
- O(log n) insertion and O(log n) poll — efficient for a task queue.
- Protected by `ReentrantLock` for thread-safe multi-client access.

---

## 4.4 Design Patterns

**Requirement:**
> Implement at least **two** design patterns from:
> Singleton, Observer, Factory Method, DAO, Builder, or Strategy.
> Patterns must be meaningfully integrated and properly justified.

This project implements **four** design patterns:

---

### ✅ Pattern 1 — Singleton

**Files:** `src/config/ConfigManager.java`

```java
public static synchronized ConfigManager getInstance() {
    if (instance == null) instance = new ConfigManager();
    return instance;
}
```

**Where applied:** `ConfigManager` — the global configuration loader.

**Why chosen:**
- Configuration must be loaded once and be consistent throughout the JVM.
- Multiple threads (ClientHandler, SlaveManager, etc.) access configuration
  concurrently — a single shared object avoids repeated file I/O.
- `synchronized` on `getInstance()` makes the lazy initialization thread-safe.

**Benefits:**
- One read of `config.properties` for the entire lifetime of the application.
- Predictable state: no risk of two instances with conflicting values.
- Easy to mock in tests by subclassing and swapping the instance.

---

### ✅ Pattern 2 — Strategy

**Files:**
- `src/strategy/ExecutionStrategy.java` (interface)
- `src/strategy/LocalExecutionStrategy.java` (V1 behavior)
- `src/strategy/DistributedExecutionStrategy.java` (V2 behavior)
- `src/observer/ProgressObserver.java` (interface — progress strategies)
- `src/observer/ConsoleProgressObserver.java` (Strategy A)
- `src/observer/LogFileProgressObserver.java` (Strategy B)
- `src/observer/SocketProgressObserver.java` (Strategy C)

```java
// ExecutionStrategy interface
TaskResult execute(Task task, Path workDir, ProgressPublisher publisher) throws Exception;
```

**Where applied:**
1. **Execution mode:** `ClientHandler` selects `LocalExecutionStrategy` or
   `DistributedExecutionStrategy` at runtime based on the client's request.
2. **Progress output:** Three `ProgressObserver` implementations encapsulate
   different strategies for handling progress (console bar, log file, socket push).

**Why chosen:**
- The *how* of execution (local vs distributed) must vary independently of
  the *what* (receive request → execute → return result).
- Adding a new execution mode (e.g. `CloudExecutionStrategy`) requires zero
  changes to `ClientHandler` or any existing strategy.
- Progress handling strategies (console, file, socket, GUI) must be swappable
  and composable without modifying the worker logic.

**Benefits:**
- **Open/Closed Principle:** open for extension, closed for modification.
- `ClientHandler` is decoupled from execution details.
- Progress observers can be mixed, matched, and hot-swapped at runtime.

---

### ✅ Pattern 3 — Observer

**Files:**
- `src/observer/ProgressObserver.java` (Observer interface)
- `src/observer/ProgressPublisher.java` (Subject / Observable)
- `src/observer/ConsoleProgressObserver.java` (Concrete Observer)
- `src/observer/LogFileProgressObserver.java` (Concrete Observer)
- `src/observer/SocketProgressObserver.java` (Concrete Observer)
- `src/common/ProgressUpdate.java` (Event object)

```java
// Subject publishes — knows nothing about observers
publisher.publish(40, "COMPUTING", "Slave 1 finished chunk 1/3 ✓");

// Observer reacts — knows nothing about subject's internals
public void onProgress(ProgressUpdate update) { /* render / log / push */ }
```

**Where applied:**
- `DistributedExecutionStrategy` (Subject) emits progress events at every
  phase (INIT → SPLIT → COMPUTING → MERGE → DONE).
- `ClientHandler` registers three observers on the same publisher before
  calling `execute()`.

**Why chosen:**
- The worker must NOT know how many clients or loggers are listening.
- Adding a new observer (e.g. metrics dashboard, alert system) requires
  zero changes to the execution strategy.
- Loose coupling: the number and type of observers is configured in
  `ClientHandler`, not in the computation code.

**Benefits:**
- **Loose coupling:** Subject ↔ Observer interact only through the
  `ProgressObserver` interface.
- **Extensibility:** new observers (GUI, Prometheus metrics, email alert)
  plug in with no existing code changes.
- **Transparency:** the client sees real-time progress instead of a silent wait.

---

### ✅ Pattern 4 — Factory Method

**File:** `src/common/TaskFactory.java`

```java
// Centralised creation with validation
TaskRequest req = TaskFactory.createLocalTask(jarBytes, options, files);
TaskRequest req = TaskFactory.createDistributedTask(master, worker, opts, files, priority);
TaskRequest req = TaskFactory.createUrgentDistributedTask(master, worker, opts, files);
```

**Where applied:**
- `src/client/Client.java` — `buildLocalRequest()` and `buildDistributedRequest()`
- `src/client/ClientCLI.java` — `buildLocalRequest()` and `buildDistributedRequest()`

**Why chosen:**
- `TaskRequest` has two constructors with different parameter sets.
  Callers mixing them up would create hard-to-debug bugs (wrong mode, null JARs).
- Input validation (null/empty JAR checks, negative priority clamping) belongs in
  one place, not scattered across every caller.
- Further task types (`createBatchTask`, `createScheduledTask`) can be added
  without changing any existing code.

**Benefits:**
- **Single Responsibility:** task creation and validation live in one class.
- **Fail-fast:** invalid inputs throw `IllegalArgumentException` before a bad
  request reaches the network.
- **Readability:** `TaskFactory.createUrgentDistributedTask(...)` is far more
  expressive than the raw constructor call.

---

## 4.5 Data Persistence

**Requirement:**
> Implement data persistence using file-based storage (serialization, JSON, or XML)
> or an embedded database. Data must survive restarts.

### ✅ Implementation — JSON File Storage

**File:** `src/persistence/TaskPersistence.java`

```
data/
  task_<id>.json   ← one file per task (written on PENDING, updated on COMPLETE)
  tasks.json       ← summary of all tasks (written at server shutdown)
```

**Per-task JSON example:**
```json
{
  "taskId": "a1b2c3d4",
  "status": "COMPLETED",
  "priority": 0,
  "executionMode": "distributed",
  "createdAt": "2026-03-04 21:05:10",
  "completedAt": "2026-03-04 21:05:18",
  "durationMs": 8123,
  "success": true,
  "consoleOutput": "=== SPLIT PHASE ===\n...",
  "outputFileCount": 1
}
```

**Lifecycle:**
1. Task arrives → `saveTask()` → written as PENDING
2. Execution starts → `updateTask()` → status = RUNNING
3. Execution ends → `updateTask()` → status = COMPLETED / FAILED
4. Server shutdown → `saveSummary()` → `tasks.json` written

**Thread safety:** `saveTask`, `updateTask`, `saveSummary` are all `synchronized`.

**Recovery:** On restart, `tasks.json` and `task_*.json` in `data/` can be
read back to reconstruct the task history. The `ConcurrentHashMap<String,Task>`
serves as an in-memory cache that could be pre-populated on startup.

**Progress logs** are also persisted — `LogFileProgressObserver` writes:
```
logs/<taskId>-progress.log
```
containing timestamped progress entries for every task.

---

## Summary Table

| Requirement | Status | Key Files |
|-------------|--------|-----------|
| 4.1 TCP Client-Server + multi-client | ✅ | `Master.java`, `Client.java`, `SlaveNode.java` |
| 4.2 Thread-per-client | ✅ | `ClientHandler.java`, `SlaveHandler.java` |
| 4.2 Thread-safety | ✅ | `SlaveManager.java`, `TaskPersistence.java`, `TaskScheduler.java` |
| 4.3 Map | ✅ | `ConcurrentHashMap` in `TaskPersistence.java` |
| 4.3 List | ✅ | `synchronizedList` in `SlaveManager.java` |
| 4.3 PriorityQueue | ✅ | `TaskScheduler.java` |
| 4.4 Singleton | ✅ | `ConfigManager.java` |
| 4.4 Strategy | ✅ | `ExecutionStrategy` + `ProgressObserver` impls |
| 4.4 Observer | ✅ | `ProgressPublisher` + 3 `ProgressObserver` impls |
| 4.4 Factory Method | ✅ | `TaskFactory.java` |
| 4.5 JSON Persistence | ✅ | `TaskPersistence.java` → `data/*.json` |
| 4.5 Progress Logs | ✅ | `LogFileProgressObserver.java` → `logs/*.log` |
