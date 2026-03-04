# Distributed Task Execution System — Technical Documentation

**Project Title:** Distributed Task Execution Framework in Java  
**Version:** 2 (with Progress Tracking — Observer Pattern)  
**Language:** Java (SE 11+)  
**Date:** March 2026  

---

## Table of Contents

1. [Project Overview](#1-project-overview)  
2. [System Architecture](#2-system-architecture)  
3. [Communication Layer](#3-communication-layer)  
4. [Multithreading](#4-multithreading)  
5. [Collections Justification](#5-collections-justification)  
6. [Design Patterns](#6-design-patterns)  
7. [Configuration System](#7-configuration-system)  
8. [Data Persistence](#8-data-persistence)  
9. [Execution Flow](#9-execution-flow)  
10. [Project Structure](#10-project-structure)  
11. [Advantages and Scalability](#11-advantages-and-scalability)  
12. [Conclusion](#12-conclusion)  

---

## 1. Project Overview

### 1.1 Goal of the System

This project implements a **generic distributed task execution framework** in Java. The system enables any client to submit arbitrary computation tasks — packaged as executable JAR files — to a networked server, which executes them and returns the results. The framework is deliberately computation-agnostic: the server does not contain or know any algorithm in advance. All executable logic is dynamically injected at runtime by the client.

### 1.2 Problem It Solves

Traditional server-client execution systems require the server to have prior knowledge of the algorithms it will run. This creates a tight coupling between client needs and server capabilities, demanding recompilation or redeployment any time a new type of computation is required.

This system solves that problem by treating the server as a **remote program executor**: the client sends not just data, but also the program (as a compiled JAR) along with its input files. The server simply receives, stores, and executes the JAR, then returns whatever output files it produces. This paradigm is sometimes referred to as **code-on-demand** or **mobile computation**.

### 1.3 General Idea of Dynamic Task Execution

The core execution model follows this principle:

```
Client submits:
  ┌─────────────────────────────┐
  │  JAR file (the algorithm)   │
  │  Options (e.g. -product)    │
  │  Input files (e.g. A.txt)   │
  └─────────────────────────────┘
                │
                ▼
Server receives, writes to temp directory, runs:
   java -jar task.jar [options] [input files]
                │
                ▼
Server collects output files and sends them back to client.
```

The server is therefore a **universal executor**. The same server instance can handle matrix multiplication, image filtering, text encryption, or any other computation — without modification — as long as the computation is packaged as a self-contained executable JAR.

---

## 2. System Architecture

The system was developed in two evolutionary versions, each building on the previous one without discarding its foundations.

### 2.1 Version 1 — Client–Server Architecture

Version 1 follows a classical **Client–Server** pattern over TCP sockets. A single server (later renamed "Master") accepts connections from clients, receives task packages, executes the task locally using Java's `ProcessBuilder`, and returns the output files.

```
┌────────────┐        TCP Socket        ┌────────────────┐
│   Client   │ ──────────────────────► │     Server      │
│            │  JAR + Options + Files   │  (local exec)   │
│            │ ◄────────────────────── │                 │
└────────────┘      Result Files        └────────────────┘
```

**Characteristics of Version 1:**
- Single machine execution
- One server, one or many clients (each handled in its own thread)
- Server executes all tasks locally via `ProcessBuilder`
- No concept of slave nodes or distributed work
- Foundation for Version 2

### 2.2 Version 2 — Master–Slave Distributed Architecture

Version 2 extends Version 1 by transforming the server into a **Master node** capable of distributing computation to a pool of **Slave nodes**. The core idea is to split large input data into chunks, send each chunk to a different slave for parallel processing, then merge the partial results into a final output.

```
┌────────────┐
│   Client   │
└────┬───────┘
     │  TCP (port 5000)
     ▼
┌────────────────────────────────────┐
│          Master Node               │
│  ┌─────────────┐ ┌──────────────┐  │
│  │TaskScheduler│ │SlaveManager  │  │
│  │(PriorityQ.) │ │(dynamic pool)│  │
│  └─────────────┘ └──────────────┘  │
│  ┌───────────────────────────────┐  │
│  │     ExecutionStrategy         │  │
│  │  Local | Distributed          │  │
│  └───────────────────────────────┘  │
└──────┬───────────────────────────┘
       │  Parallel TCP connections (port 6001, 6002, ...)
       ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  Slave Node  │   │  Slave Node  │   │  Slave Node  │
│  (port 6001) │   │  (port 6002) │   │  (port 6003) │
└──────────────┘   └──────────────┘   └──────────────┘
```

**Key innovation of Version 2:** The Master listens on **two separate ports**:
- **Port 5000** — accepts client connections (task submissions)
- **Port 5001** — accepts slave self-registration (dynamic slave pool management)

Slaves **register themselves** at startup by connecting to port 5001 and sending a `SlaveRegistration` object containing their host and port. This eliminates the need to pre-configure slave addresses in the Master's configuration file, making the system truly dynamic and scalable.

### 2.3 Responsibilities of Each Node

| Node | Responsibility |
|------|----------------|
| **Client** | Read JAR files and input data from disk; submit task to Master; display real-time progress; save output files |
| **Master** | Accept client connections; schedule tasks by priority; choose execution strategy; coordinate slaves; persist task history; publish progress events |
| **Slave** | Register with Master; wait for task chunks; execute worker JAR locally; return result files |

### 2.4 Task Flow Through the System

The following diagram represents the high-level flow of a distributed task:

```
Client               Master               Slave(s)
  │                    │                     │
  │──TaskRequest──────►│                     │
  │                    │ (schedule, persist)  │
  │                    │──chunk 1────────────►│ (slave #1)
  │                    │──chunk 2──────────────────────────►│ (slave #2)
  │◄─ProgressUpdate ──│                     │
  │◄─ ProgressUpdate─ │◄──result 1──────────│
  │                    │◄──result 2──────────────────────────│
  │                    │ (merge results)      │
  │◄──────────────── TaskResult ─────────────│
  │                    │                     │
```

---

## 3. Communication Layer

### 3.1 Why TCP Sockets Are Used

The system uses Java's **TCP sockets** (`java.net.Socket`, `java.net.ServerSocket`) as its communication backbone. TCP was chosen over UDP for the following reasons:

1. **Reliability**: TCP guarantees ordered, lossless delivery of bytes. Since task data includes JAR files and input data that must arrive intact and in order, any packet loss would corrupt the computation.
2. **Connection-oriented semantics**: TCP establishes a persistent, bidirectional connection between peers, which is ideal for the request–response model used here (one connection per task).
3. **Flow control**: The TCP stack automatically handles backpressure, preventing the sender from overwhelming the receiver when transferring large JAR files.
4. **Simplicity**: Java's built-in `ObjectInputStream` / `ObjectOutputStream` can be layered directly on top of TCP streams, enabling serialized Java objects to be exchanged transparently.

### 3.2 How Files and JARs Are Transferred

All data exchanged between nodes — JAR bytecode, input files, and output files — is serialized using Java's **Object Serialization** mechanism. The key design decision is that files are **not transferred by path or reference**, but by their **raw byte content**:

- A file is wrapped in a `FileData` object containing two fields: `String fileName` and `byte[] fileContent`.
- `FileData` implements `Serializable`, making it directly writable to an `ObjectOutputStream`.
- This design allows the system to work transparently across machines, networks, and file system boundaries.

**Transfer path for a typical local task:**

```
Client:
  Files.readAllBytes(path)  →  byte[]
  new FileData(name, bytes) →  FileData object
  ObjectOutputStream.writeObject(request)  // serialized over TCP

Master:
  ObjectInputStream.readObject()  // deserialized from TCP
  Files.write(workDir.resolve(fd.getFileName()), fd.getFileContent())
```

### 3.3 Request and Response Structure

The communication protocol is entirely object-based. All exchanged messages are serializable Java classes defined in the `common` package:

| Object | Direction | Purpose |
|--------|-----------|---------|
| `TaskRequest` | Client → Master | Contains JAR bytes, options, input files, and execution mode |
| `ProgressUpdate` | Master → Client | Real-time progress notification (percent, phase, message) |
| `TaskResult` | Master → Client | Final outcome (success flag, console output, output files) |
| `SlaveRegistration` | Slave → Master | Slave self-registration (host, port) |

The response stream is **multiplexed**: the Master sends zero or more `ProgressUpdate` objects followed by exactly one `TaskResult`. The client reads objects in a loop and distinguishes their types using `instanceof`:

```java
while (true) {
    Object obj = in.readObject();
    if (obj instanceof ProgressUpdate) {
        renderProgress((ProgressUpdate) obj);
    } else if (obj instanceof TaskResult) {
        result = (TaskResult) obj;
        break;
    }
}
```

This protocol is simple, extensible, and requires no custom framing or length-prefix headers.

---

## 4. Multithreading

### 4.1 Thread-per-Client Model

The Master server implements the **Thread-per-Client** pattern: for every new client connection that arrives on `ServerSocket.accept()`, a dedicated `ClientHandler` thread is spawned and started. This allows the Master to serve multiple clients simultaneously without any client blocking another.

```java
while (true) {
    Socket socket = clientSocket.accept();
    ClientHandler handler = new ClientHandler(socket, clientId, ...);
    handler.start();  // new OS thread, fully independent
}
```

`ClientHandler` extends `Thread` and overrides its `run()` method to handle the full lifecycle of one client session: receive task, schedule, execute, and respond.

### 4.2 How Concurrency Is Handled

The system uses several concurrency mechanisms:

1. **Registration thread**: The Master spawns a separate daemon thread solely to listen for slave registrations on port 5001. This thread runs in parallel with the main client-listening thread and does not block it.

2. **Per-slave dispatch threads in `SlaveManager.distributeWork()`**: When distributing chunks to slaves, the `SlaveManager` spawns one `Thread` per chunk and uses `thread.join(timeoutMs)` to wait for all of them to complete before proceeding to the merge phase. This achieves true parallel execution across slaves.

3. **`SocketProgressObserver` synchronization**: When sending `ProgressUpdate` objects from multiple threads (one per slave chunk), the `SocketProgressObserver` uses a `synchronized (out)` block on the `ObjectOutputStream` to prevent interleaved or corrupted writes.

### 4.3 Thread Safety

Thread safety is ensured through a combination of techniques:

| Component | Mechanism | Reason |
|-----------|-----------|--------|
| `TaskScheduler` (PriorityQueue) | `ReentrantLock` | PriorityQueue is not thread-safe; lock prevents concurrent mutation |
| `SlaveManager` (slave list) | `Collections.synchronizedList()` + `synchronized` methods | Multiple `ClientHandler` threads may add/remove slaves simultaneously |
| `TaskPersistence` (task map) | `ConcurrentHashMap` | Allows concurrent reads with safe atomic puts; `saveTask`/`updateTask` are also `synchronized` |
| `ProgressPublisher` (observer list) | `synchronized` block on snapshot copy | Prevents ConcurrentModificationException while iterating observers |
| `ConfigManager` (singleton init) | `synchronized` on `getInstance()` | Ensures exactly one instance is created even under concurrent first access |

### 4.4 Shared Resources

The following objects are shared across threads and require synchronization:

- **`SlaveManager`** — the dynamic slave pool is accessed by the registration thread (writes) and all `ClientHandler` threads (reads and removes on failure).
- **`TaskScheduler`** — the `PriorityQueue<Task>` is written by every `ClientHandler` thread via `submitTask()` and read by the same threads via `getNextTask()`.
- **`TaskPersistence`** — the `ConcurrentHashMap<String, Task>` is accessed by all `ClientHandler` threads when saving and updating task state.
- **`ObjectOutputStream out`** in `SocketProgressObserver` — shared between the main execution thread and the per-slave dispatch threads.

---

## 5. Collections Justification

### 5.1 `Map` — Task Storage (`ConcurrentHashMap<String, Task>`)

**Where:** `TaskPersistence.taskMap`

**Why `Map`:** A `Map` provides O(1) average-time lookup by key. The key is the task's unique 8-character identifier (UUID-derived), and the value is the full `Task` object. This structure is ideal for a task registry where the most common operations are: insert a new task, update an existing task by ID, and retrieve a task by ID — all O(1) operations.

**Why `ConcurrentHashMap` specifically:** Multiple `ClientHandler` threads run simultaneously, each potentially saving or updating tasks concurrently. `ConcurrentHashMap` provides thread-safe access for these concurrent writes without requiring a global lock, resulting in better throughput than a `synchronized HashMap`.

### 5.2 `List` — Slave Pool, File Collections, Thread Collections

**Where:** `SlaveManager.slaves` (a `synchronizedList<SlaveInfo>`), file lists throughout the strategies, and thread lists in `distributeWork()`.

**Why `List`:**

- **Slave pool**: Slaves must be iterable in order (for round-robin distribution using index modulo), and the pool is dynamically sized (slaves can be added or removed at runtime). A `List` supports indexed access (`get(i % numSlaves)`), which is necessary for the round-robin assignment. A `Set` would not support indexed access; a `Map` would be over-engineered.
- **File collections** (`List<FileData>`, `List<String>`): Files must be ordered (e.g., chunks must be sent to slaves in deterministic order and merged in the correct sequence). A `List` preserves insertion order, which is critical for result correctness.
- **Thread list** in `distributeWithProgress()`: All dispatch threads are stored in a `List<Thread>` so that the manager can call `thread.join()` on each of them in order, waiting for all to complete before merging.

**Why `synchronizedList` for the slave pool:** Unlike `ConcurrentHashMap`, `List` operations like iteration are not thread-safe by default. `Collections.synchronizedList()` wraps the `ArrayList` so that all mutation operations (`add`, `remove`) are automatically synchronized.

### 5.3 `PriorityQueue` — Task Scheduling (`PriorityQueue<Task>`)

**Where:** `TaskScheduler.taskQueue`

**Why `PriorityQueue`:** The system supports task priorities: a client can submit a task with `priority=10` (urgent) and it should be executed before a task with `priority=0` (normal), regardless of arrival order. A `PriorityQueue` is the canonical data structure for this use case — it maintains a heap that always returns the highest-priority element first in O(log n) time.

The `Task` class implements `Comparable<Task>` with comparator logic that reverses natural order (higher integer priority = processed first):

```java
@Override
public int compareTo(Task other) {
    return Integer.compare(other.request.getPriority(), this.request.getPriority());
}
```

A regular `Queue` or `LinkedList` would serve tasks in FIFO order regardless of priority. A `TreeSet` would introduce uniqueness constraints inappropriate here. The `PriorityQueue` is the correct and idiomatic choice.

**Thread safety via `ReentrantLock`:** Since `PriorityQueue` is not thread-safe, all access to it (`submitTask`, `getNextTask`, `getPendingCount`) is guarded by a `ReentrantLock`, ensuring no two threads corrupt the heap simultaneously.

---

## 6. Design Patterns

### 6.1 Singleton Pattern — `ConfigManager`

**Location:** `src/config/ConfigManager.java`

**What it is:** The Singleton pattern ensures that a class has exactly one instance throughout the application lifecycle, providing a global access point to that instance.

**Where it is used:** `ConfigManager` is the single source of truth for all external configuration parameters. It is consumed by virtually every component in the system: `Master`, `SlaveNode`, `SlaveManager`, `TaskPersistence`, `ClientCLI`, and the execution strategies.

**How it is implemented:**

```java
public class ConfigManager {
    private static ConfigManager instance;  // the single instance

    private ConfigManager() {
        // private: prevents direct instantiation
        loadConfig("config/config.properties");
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();  // lazy initialization
        }
        return instance;
    }
}
```

The constructor is private, preventing external code from calling `new ConfigManager()`. The `getInstance()` method is `synchronized` to guarantee safe initialization even when two threads call it simultaneously for the first time.

**Why it is used:**

1. **Consistency**: Without a Singleton, each class that needs configuration would either hard-code values or load the properties file independently, risking inconsistencies if the file changes or if different instances read different values.
2. **Resource efficiency**: Loading a properties file from disk involves I/O. A Singleton ensures this cost is paid only once.
3. **Global access without coupling**: Components access `ConfigManager.getInstance()` without being injected with a reference, simplifying their constructors.

**Benefits for maintainability and scalability:** The externalized configuration (discussed in Section 7) combined with the Singleton pattern means that changing a port number, a timeout value, or a directory path requires editing exactly one file, and the entire system picks up the change on next startup without any code modification.

### 6.2 Strategy Pattern — `ExecutionStrategy`

**Location:** `src/strategy/ExecutionStrategy.java` (interface), `src/strategy/LocalExecutionStrategy.java`, `src/strategy/DistributedExecutionStrategy.java`

**What it is:** The Strategy pattern defines a family of algorithms, encapsulates each one, and makes them interchangeable. It allows the algorithm to vary independently from the clients that use it.

**Where it is used:** The `ClientHandler` must execute tasks in one of two possible modes: local (Version 1 behavior — run the JAR directly on the Master machine) or distributed (Version 2 — split input, delegate to slaves, merge results). Both modes are encapsulated as implementations of the `ExecutionStrategy` interface:

```java
public interface ExecutionStrategy {
    TaskResult execute(Task task, Path workDir, ProgressPublisher publisher) throws Exception;
    String getStrategyName();
}
```

At runtime, `ClientHandler` selects the strategy based on the client's request:

```java
ExecutionStrategy strategy;
if (nextTask.getRequest().isDistributed()) {
    strategy = distributedStrategy;
} else {
    strategy = localStrategy;
}
result = strategy.execute(nextTask, workDir, publisher);
```

The `ClientHandler` does not contain any `if/else` logic about *how* a task is executed — it only decides *which strategy* to use, then delegates entirely. The execution details are fully encapsulated inside each strategy class.

**Why it is used:**

1. **Open/Closed Principle**: New execution modes (e.g., a `GPUExecutionStrategy` or a `CloudExecutionStrategy`) can be added by creating a new class implementing `ExecutionStrategy`, without modifying `ClientHandler` at all.
2. **Testability**: Each strategy can be instantiated and tested in complete isolation, independent of the networking infrastructure.
3. **Separation of concerns**: The `ClientHandler` focuses on protocol handling (receive, schedule, respond), while execution strategies focus on computation coordination.

**Benefits for maintainability and scalability:** When Version 2 was added to Version 1, the `ClientHandler` required no structural changes — only a new `DistributedExecutionStrategy` was implemented and injected. This illustrates how the Strategy pattern makes the system horizontally extensible with minimal risk of regression.

### 6.3 Observer Pattern — Progress Tracking

**Location:** `src/observer/`

**What it is:** The Observer pattern (also known as Publish-Subscribe) defines a one-to-many dependency between objects. When the subject's state changes, all registered observers are notified automatically.

**Where it is used:** When a task is executing, the `ExecutionStrategy` publishes progress events through a `ProgressPublisher` (the Subject). Three concrete observers are registered:

| Observer | Behavior |
|----------|----------|
| `ConsoleProgressObserver` | Renders a visual progress bar on the Master's stdout |
| `LogFileProgressObserver` | Appends a timestamped line to a per-task log file in `logs/` |
| `SocketProgressObserver` | Serializes the `ProgressUpdate` and pushes it over the socket to the client |

```
ProgressPublisher (Subject)
    │
    ├──► ConsoleProgressObserver.onProgress()
    ├──► LogFileProgressObserver.onProgress()
    └──► SocketProgressObserver.onProgress()
```

The `ExecutionStrategy` calls only `publisher.publish(percent, phase, message)`. It has zero awareness of who is listening — whether it is the console, a file, a socket, or a database. This is the core benefit of the Observer pattern: **loose coupling between the event source and event consumers**.

**Why it is used:**

1. **Loose coupling**: Adding a new monitoring channel (e.g., a Prometheus metric exporter) requires only implementing `ProgressObserver.onProgress()` and registering the new observer — no changes to the strategies.
2. **Fault isolation**: The `ProgressPublisher.publish()` method catches exceptions from individual observers in a try/catch, so a broken observer (e.g., a disconnected socket) cannot disrupt the execution or other observers.
3. **Real-time UX**: The client receives a live animated progress bar, providing immediate feedback and a professional user experience.

### 6.4 Factory Method Pattern — `TaskFactory`

**Location:** `src/common/TaskFactory.java`

**What it is:** The Factory Method pattern centralizes object creation, decoupling the caller from the constructor details and validation logic.

**Where it is used:** Both `Client` and `ClientCLI` use `TaskFactory.createLocalTask()` or `TaskFactory.createDistributedTask()` to build `TaskRequest` objects. The factory enforces validation (non-null, non-empty JAR bytes) and applies sensible defaults (null options array → empty array, negative priority → 0).

**Why it is used:** Without the factory, validation logic would be duplicated in every place that constructs a `TaskRequest`, increasing the risk of inconsistency and bugs. The factory also provides a convenience method `createUrgentDistributedTask()` for high-priority jobs, demonstrating how the factory can encode domain knowledge about valid configurations.

---

## 7. Configuration System

### 7.1 The Configuration File: `config/config.properties`

All deployment-specific parameters are externalized into a Java Properties file located at `config/config.properties`. The file is loaded at startup by the `ConfigManager` Singleton:

```properties
# Master ports
server.host=localhost
server.port=5000
server.reg.port=5001
server.maxClients=50

# Slave behavior
slave.host=localhost
slave.timeout=10000
slave.retry=2

# Threading
thread.pool.size=10

# Directory paths
temp.dir=tmp
data.dir=data
logs.dir=logs

# Progress tracking
progress.enabled=true
progress.min.step=1
```

### 7.2 What Values Are Externalized

| Property | Used By | Purpose |
|----------|---------|---------|
| `server.port` | Master, ClientCLI | Main port for client connections |
| `server.reg.port` | Master, SlaveNode | Port for slave self-registration |
| `server.host` | ClientCLI, SlaveNode | Address to connect to |
| `slave.timeout` | SlaveManager, DistributedStrategy | Socket timeout per slave (ms) |
| `slave.retry` | SlaveManager | Max retry attempts per failed chunk |
| `thread.pool.size` | Available for future executor service |
| `data.dir` | TaskPersistence | Directory for JSON task records |
| `logs.dir` | ClientHandler, LogFileProgressObserver | Directory for per-task log files |
| `progress.enabled` | Future: conditional progress reporting |
| `progress.min.step` | Future: throttle update frequency |

### 7.3 Why Removing Hardcoded Constants Is Important

Hardcoding values like port numbers, paths, and timeouts directly in source code creates significant operational friction:

- **No recompilation needed**: Changing a port number from `5000` to `8080` for a production environment requires only editing the properties file, not recompiling the application.
- **Environment independence**: The same JAR can be deployed on a development laptop, a staging server, and a production cluster simply by providing different `config.properties` files.
- **Readability and documentation**: The properties file serves as self-documenting infrastructure configuration, making it clear what parameters govern system behavior.
- **Separation of concerns**: Business logic in Java code is separated from operational configuration in a properties file, following the principle of separating concerns across deployment artifacts.

If `ConfigManager` fails to find the file, it gracefully falls back to hardcoded defaults via the `setDefaults()` method, ensuring the system remains functional even in minimal environments.

---

## 8. Data Persistence

### 8.1 How Tasks Are Saved

Every task processed by the system is persisted to disk in **JSON format** by the `TaskPersistence` class. Two forms of persistence exist:

1. **Individual task files**: Each task is written to `data/task_<taskId>.json`. This file is created when the task is first received (status: `PENDING`) and updated when its status changes to `RUNNING`, `COMPLETED`, or `FAILED`.

2. **Summary file**: After each task completes, `TaskPersistence.saveSummary()` writes a `data/tasks.json` file containing a JSON array summarizing all known tasks (ID, status, priority, mode, creation timestamp).

**Example individual task record:**
```json
{
  "taskId": "204ba3c9",
  "status": "COMPLETED",
  "priority": 0,
  "executionMode": "distributed",
  "createdAt": "2026-02-28 13:57:56",
  "completedAt": "2026-02-28 13:57:57",
  "durationMs": 645,
  "success": true,
  "consoleOutput": "...",
  "outputFileCount": 2
}
```

The JSON is written manually (without an external library) using a `PrintWriter`, keeping the system dependency-free while producing human-readable, standards-compliant output.

### 8.2 How Data Survives Restarts

Task records written to the `data/` directory persist across Master restarts because they are stored as flat files on disk. The in-memory `ConcurrentHashMap<String, Task>` is populated as tasks arrive at runtime, and each mutation is immediately written to disk. This means:

- If the Master crashes mid-execution, the task file will have been written with `status: RUNNING`. An operator can inspect the file to identify incomplete tasks.
- Previous task history is always available in `data/tasks.json` for audit, monitoring, or dashboards.

Note: the current implementation does not reload task records from disk on startup (the `taskMap` starts empty on each restart), but the persisted JSON files remain as a durable historical record. Implementing full recovery on startup would require iterating the `data/` directory and deserializing the JSON records — an extension that the architecture already supports.

### 8.3 Why Persistence Is Necessary

In a distributed system, persistence serves several critical functions:

1. **Auditability**: Every task that passed through the system is permanently recorded, enabling post-mortem analysis or compliance reporting.
2. **Debugging**: When a task fails, its full console output and duration are stored, allowing engineers to diagnose issues without requiring the client to reproduce them.
3. **Monitoring**: The `tasks.json` summary file can be consumed by external tools (log aggregators, dashboards) to provide operational visibility.
4. **Reliability**: In production environments, transient failures should not result in lost task history. Persisting state to disk before acknowledging task completion is a standard reliability practice.

---

## 9. Execution Flow

### 9.1 Version 1 — Local Task Execution Flow

The following is a step-by-step trace of a Version 1 (local) task:

```
Step 1: Client reads JAR file and input files from disk into byte arrays.
        TaskFactory.createLocalTask() builds and validates a TaskRequest.

Step 2: Client connects to Master on port 5000.
        ObjectOutputStream.writeObject(request) sends the serialized TaskRequest.

Step 3: Master's main loop calls ServerSocket.accept(), creating a Socket.
        A new ClientHandler thread is spawned and started.

Step 4: ClientHandler reads the TaskRequest via ObjectInputStream.
        A Task wrapper is created (assigns UUID, timestamp, priority).

Step 5: Task is submitted to TaskScheduler (inserted into PriorityQueue).
        Task is saved to disk by TaskPersistence (status: PENDING).

Step 6: TaskScheduler.getNextTask() polls the PriorityQueue (highest priority first).
        Task status updated to RUNNING; persisted.

Step 7: ClientHandler creates a temporary working directory.
        LocalExecutionStrategy is selected (executionMode == "local").

Step 8: Three ProgressObservers are registered with ProgressPublisher:
        ConsoleProgressObserver, LogFileProgressObserver, SocketProgressObserver.

Step 9: LocalExecutionStrategy.execute():
        - JAR bytes written to workDir/task.jar
        - Input file bytes written to workDir/
        - ProcessBuilder launches: java -jar task.jar [options] [files]
        - Progress events published at 5%, 30%, 50%, 90%, 100%
        - Console output captured from process stdout+stderr

Step 10: Output files collected from workDir (excluding task.jar and input files).
         TaskResult created (success flag, console output, output files as FileData[]).

Step 11: Task status updated to COMPLETED (or FAILED); persisted.
         Summary written to data/tasks.json.

Step 12: TaskResult serialized and sent back to client via ObjectOutputStream.

Step 13: Client reads objects from the stream:
         ProgressUpdate objects → rendered as animated progress bar
         TaskResult → loop exits; files saved to results/

Step 14: Working directory cleaned up (deleted recursively).
         Socket closed on both ends.
```

### 9.2 Version 2 — Distributed Task Execution Flow

Distributed execution adds three phases around the local execution: **split**, **parallel dispatch**, and **merge**.

```
Step 1–8: Same as Version 1.
           DistributedExecutionStrategy is selected (executionMode == "distributed").

Step 9: DistributedExecutionStrategy.execute():

  Phase A — SETUP (5%):
  - masterJarBytes → workDir/master.jar
  - workerJarBytes → workDir/worker.jar
  - input files → workDir/

  Phase B — SPLIT (10%→20%):
  - ProcessBuilder runs: java -jar master.jar split <numSlaves> [options] <inputFiles>
  - Master JAR produces chunk files: chunk_0.txt, chunk_1.txt, ...
  - Chunk files collected as FileData[] objects.

  Phase C — DISTRIBUTE (25%→80%):
  - For each chunk, a dispatch thread is created:
      TaskRequest(workerJarBytes, options, [chunkFile]) sent to a Slave
  - Round-robin assignment: slave = pool.get(i % numSlaves)
  - Threads run in parallel; join(timeoutMs) waits for all
  - ProgressUpdate published as each slave completes

  On the Slave side (simultaneously for each slave):
    * Receive TaskRequest
    * Write worker.jar and chunk file to temp directory
    * ProcessBuilder: java -jar worker.jar [options] chunk_N.txt
    * Collect output files
    * Send TaskResult back to Master

  Phase D — MERGE (85%→95%):
  - Slave result files (partial results) written to workDir as result_0.*, result_1.*, ...
  - ProcessBuilder runs: java -jar master.jar merge result_0 result_1 ...
  - Master JAR combines partial results into final output file

  Phase E — DONE (100%):
  - Final output files collected from workDir
  - Full console summary built (split output + slave outputs + merge output)
  - TaskResult returned

Step 10–14: Same as Version 1 (persist, send TaskResult, client saves files).
```

---

## 10. Project Structure

The project follows a standard Maven-like source layout, with packages mapped to architectural concerns:

```
java_v1/
├── src/                          ← All Java source files
│   ├── client/
│   │   ├── Client.java           ← CLI client (command-line arguments)
│   │   └── ClientCLI.java        ← Interactive CLI client (menu-driven)
│   │
│   ├── common/                   ← Shared data model (used by all nodes)
│   │   ├── FileData.java         ← File transfer container (name + bytes)
│   │   ├── ProgressUpdate.java   ← Real-time progress event message
│   │   ├── SlaveRegistration.java← Slave self-registration message
│   │   ├── Task.java             ← Task wrapper with lifecycle metadata
│   │   ├── TaskFactory.java      ← Factory for creating TaskRequest objects
│   │   ├── TaskRequest.java      ← Task submission payload (V1 and V2)
│   │   └── TaskResult.java       ← Execution result (success, output, files)
│   │
│   ├── config/
│   │   └── ConfigManager.java    ← Singleton; loads config.properties
│   │
│   ├── master/
│   │   ├── Master.java           ← Entry point; opens two ServerSockets
│   │   ├── ClientHandler.java    ← Per-client thread; orchestrates execution
│   │   ├── SlaveManager.java     ← Dynamic slave pool; work distribution
│   │   └── TaskScheduler.java    ← Priority queue with ReentrantLock
│   │
│   ├── observer/                 ← Observer Pattern components
│   │   ├── ProgressObserver.java ← Observer interface
│   │   ├── ProgressPublisher.java← Subject; manages and notifies observers
│   │   ├── ConsoleProgressObserver.java ← Renders progress bar on stdout
│   │   ├── LogFileProgressObserver.java ← Writes timestamped log entries
│   │   └── SocketProgressObserver.java  ← Pushes updates to client socket
│   │
│   ├── persistence/
│   │   └── TaskPersistence.java  ← JSON file persistence; ConcurrentHashMap
│   │
│   ├── slave/
│   │   ├── SlaveNode.java        ← Entry point; registers with Master
│   │   └── SlaveHandler.java     ← Per-task thread; executes worker JAR
│   │
│   └── strategy/                 ← Strategy Pattern components
│       ├── ExecutionStrategy.java← Strategy interface
│       ├── LocalExecutionStrategy.java      ← V1 local execution
│       └── DistributedExecutionStrategy.java← V2 split/dispatch/merge
│
├── config/
│   └── config.properties         ← External configuration (ports, paths, timeouts)
│
├── tasks/                        ← Prebuilt task JARs and their source code
│   ├── MatrixComputation.jar     ← V1: local matrix multiplication/addition
│   ├── MatrixMaster.jar          ← V2: matrix split/merge coordinator
│   ├── MatrixWorker.jar          ← V2: matrix chunk processor (slave-side)
│   ├── Filters.jar               ← V1: image grayscale/brightness filter
│   ├── Encrypt.jar               ← V1: Caesar cipher text encryption
│   ├── matrix/                   ← Source code for matrix JARs
│   ├── filters/                  ← Source code for image filter JAR
│   └── encrypt/                  ← Source code for encryption JAR
│
├── data/                         ← Persisted task records (JSON files)
│   ├── tasks.json                ← Summary of all tasks
│   └── task_<id>.json            ← One file per task execution
│
├── logs/                         ← Per-task progress log files
│   └── <taskId>-progress.log     ← Timestamped progress events
│
├── results/                      ← Client output files (saved locally)
│
├── build/                        ← Compiled .class files (after build.sh)
│
├── build.sh                      ← Build script (compile + package JARs)
├── run.sh                        ← Quick run helper script
├── start-master.sh               ← Convenience script to start Master
├── start-slave.sh                ← Convenience script to start a Slave
├── start-client.sh               ← Convenience script to start Client
│
├── README.MD                     ← Version 1 overview and design notes
├── from1to2.md                   ← Migration guide from V1 to V2
└── REQUIREMENTS.md               ← Full project requirements specification
```

---

## 11. Advantages and Scalability

### 11.1 How the System Scales

The system is designed with horizontal scalability as a first-class concern:

**Slave pool elasticity**: Slaves self-register at startup by connecting to the Master's registration port. There is no fixed slave count in the configuration — a new slave can join the pool at any time simply by running `java -cp build slave.SlaveNode <port>`. The Master's `SlaveManager` maintains a synchronized list that grows dynamically. The round-robin distribution `pool.get(i % numSlaves)` automatically utilizes newly added slaves on the next task.

**Client concurrency**: The Thread-per-Client model allows the Master to handle any number of simultaneous clients. Each `ClientHandler` is a fully independent thread with its own task lifecycle.

**Priority-based workload management**: The `PriorityQueue`-based `TaskScheduler` ensures that high-priority tasks are always processed first, regardless of arrival order. This allows the system to support service-level tiers (e.g., premium clients get priority `10`, normal clients get priority `0`).

**Failure isolation**: If a slave fails during chunk processing, the `SlaveManager` automatically removes it from the pool (`unregisterSlave()`) and the `TaskResult` for that chunk reflects a failure. The system continues processing other chunks with the remaining slaves.

### 11.2 How New Tasks Can Be Added

Adding a new type of computation requires **zero changes to the framework**. The process is:

1. **Write a Java class** with a `main(String[] args)` method that reads input files, performs computation, and writes output files.
2. **Package it as a JAR** with a manifest specifying the `Main-Class`.
3. **Submit it via the client**:
   - For local execution: `java -cp build client.Client localhost 5000 local myTask.jar [options] -- inputFile`
   - For distributed execution: provide a `MasterJar` (split/merge logic) and a `WorkerJar` (chunk processing logic).

The framework is fully computation-agnostic. The example tasks bundled with the project (`MatrixComputation`, `Filters`, `Encrypt`, `MatrixMaster`, `MatrixWorker`) demonstrate this versatility across entirely different domains.

### 11.3 How It Could Evolve Into a Real Distributed System

The current architecture provides a solid foundation that could be extended toward production-grade distributed computing:

| Current Design | Potential Extension |
|----------------|---------------------|
| Thread-per-client | `ExecutorService` with a fixed or bounded thread pool to limit resource consumption |
| Direct slave registration | Service discovery via Zookeeper or Consul for dynamic, resilient slave management |
| JSON file persistence | Relational database (PostgreSQL) or distributed key-value store (Redis) for scalable task history |
| Single Master node | Master replication / failover using Raft/Paxos consensus for high availability |
| TCP sockets + Java Serialization | gRPC or REST API for language-agnostic interoperability |
| In-process progress events | Message broker (Kafka, RabbitMQ) for decoupled, persistent event streams |
| Simple round-robin assignment | Intelligent load balancing based on slave CPU/memory metrics |
| Manual JAR submission | Container-based task packaging (Docker images) for environment isolation |

The existing layer separation (common ↔ master ↔ slave, strategy interface, observer pattern) means many of these upgrades are isolated changes — replacing `LocalExecutionStrategy` with a containerized version, for instance, requires only implementing the existing `ExecutionStrategy` interface.

---

## 12. Conclusion

The Distributed Task Execution System presented in this document demonstrates a principled, multi-layered approach to designing a generic computation framework in Java. The key design achievements are as follows:

**Modularity**: The system is decomposed into well-defined, single-responsibility components — `ConfigManager`, `TaskScheduler`, `SlaveManager`, `TaskPersistence`, `ProgressPublisher`, and the execution strategies — that can be independently developed, tested, and replaced.

**Extensibility**: The Strategy, Observer, and Factory patterns collectively ensure that new execution modes, monitoring channels, and task types can be added without modifying existing code. The system adheres to the Open/Closed Principle at its architectural level.

**Robustness**: Thread safety is addressed at every shared resource boundary using appropriate mechanisms — `ReentrantLock`, `ConcurrentHashMap`, `synchronizedList`, and `synchronized` blocks — preventing race conditions without over-serializing access.

**Operability**: The external configuration system, structured JSON persistence, and real-time progress logging provide the operational visibility expected of production-grade software. A system administrator can inspect running state, audit past executions, and reconfigure deployment without touching source code.

**Elegance of the dynamic JAR model**: Perhaps the most distinctive architectural decision is treating the server as a universal program executor rather than an algorithm-specific service. This inversion of control — where the client decides what to compute, not the server — produces a system of remarkable generality and reusability.

In summary, this project successfully combines classical distributed systems concepts (client–server, master–slave, parallel dispatch, result aggregation) with modern software engineering practices (design patterns, thread safety, external configuration, data persistence, real-time observability) to produce a framework that is both a functional distributed computing engine and an exemplary demonstration of object-oriented design principles.

---

*Document prepared for academic submission. All source code resides in `/home/ridha/Desktop/java_v1/src/`. Build the project with `./build.sh` from the project root.*
