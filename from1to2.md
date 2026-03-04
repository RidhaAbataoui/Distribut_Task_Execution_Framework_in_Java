📘 Distributed Task Execution System
From Version 1 (Client–Server) → Version 2 (Master–Slave Distributed)
🎯 Project Objective

This project implements a generic distributed task execution framework in Java.

The system allows a client to dynamically send:

executable JAR programs

execution options

input files

to a server that runs the task and returns results.

The design evolves in two stages:

Version 1 → local execution on the server

Version 2 → distributed execution using slave nodes (OOP concept)

The goal is to progressively transform a simple executor into a scalable distributed computing system while satisfying all technical requirements:

Client–Server architecture ensuring implement OOP concept

Multithreading

Java Collections

Design Patterns

Data persistence

External configuration

🚀 Version 1 — Client/Server Execution
📌 Concept

The server behaves as a remote program executor.

It does not know any algorithms.

Instead, the client sends the computation dynamically as a JAR.

Architecture
Client  ─────────►  Server
        jar + options + files

Server:

receives task

saves files

executes:
java -jar task.jar args

returns results

Execution Example
Matrix multiplication
java -jar MatrixComputation.jar -product A.txt B.txt
Image filter
java -jar Filters.jar -gray image.png
Characteristics

simple

dynamic

single-machine execution

no distribution

foundation for Version 2

🚀 Version 2 — Master/Slave Distributed Execution
📌 Concept

The server becomes a Master node that distributes work to Slaves.

Tasks are:

split

processed in parallel

merged

Architecture
Client
   ↓
Master
   ↓
Slaves (parallel)
   ↓
Master merge
   ↓
Result
Task structure

Each distributed task provides:

Master JAR

split input

send chunks

merge results

Slave JAR

process one chunk only

Example
Client sends
SplitMultiplyMerge.jar
MatrixWorker.jar
A.txt B.txt
Master

split matrices

send to slaves

Slaves

multiply submatrices

Master

merge and return result

🔄 How to Move from Version 1 → Version 2

Version 2 is NOT a rewrite.

It is simply:

Version 2 = Version 1 + task distribution
Step 1 — Keep Version 1 intact

Do NOT change:

sockets

client protocol

jar execution logic

threading

These are reused.

Step 2 — Rename Server → Master

Conceptual change only.

Server becomes coordinator.

Step 3 — Add SlaveNode application

Create a new program:

SlaveNode:

waits for master connection

receives jar + files

executes locally

returns result

A slave is simply a mini Version 1 executor.

Step 4 — Split tasks

Instead of one jar:

Before:

Task.jar

After:

MasterTask.jar
WorkerTask.jar
Step 5 — Add slave pool

Master maintains a list of slaves and distributes jobs.

Step 6 — Add scheduler

Use task queue + priority to dispatch jobs.

🧩 Technical Requirements Implementation
4.1 Architecture — Client/Server

Implemented using:

Java TCP sockets

Object streams

File byte transfer

Supports:

multiple simultaneous clients

dynamic tasks

4.2 Multithreading
Thread-per-client pattern

Each connection:

new ClientHandler(socket).start()

Benefits:

concurrency

scalability

non-blocking server

Thread safety

Shared structures use:

ConcurrentHashMap

synchronized blocks

thread-safe collections

Prevents race conditions.

4.3 Collections
Map
Map<TaskID, Task>

Stores tasks and fast lookup.

List
List<SlaveNode>

Stores connected slaves.

PriorityQueue
PriorityQueue<Task>

Schedules tasks by priority.

These choices match functional needs and improve performance.

4.4 Design Patterns
Singleton — ConfigManager / ServerManager

Only one instance:

configuration

server state

Benefits:

global access

consistent settings

avoids duplicates

Strategy — Execution Mode

Defines:

ExecutionStrategy

Implementations:

LocalExecutionStrategy (V1)

DistributedExecutionStrategy (V2)

Benefits:

switch between modes easily

cleaner architecture

extensible

(Optionally Factory for creating tasks)

4.5 Data Persistence

Tasks and history are stored using:

JSON files

Example:

data/tasks.json

Stores:

task id

status

result

timestamps

Benefits:

recover after restart

persistent logs

reliability

⚙️ Configuration File (NEW FEATURE)
Goal

Remove all hardcoded values.

Move environment settings to:

config/config.properties
Folder structure
project/
 ├─ client/
 ├─ master/
 ├─ slave/
 ├─ config/
 │   └─ config.properties
 ├─ data/
 └─ logs/
Example config.properties
# Server
server.port=5000
server.maxClients=50

# Slaves
slave.timeout=10000
slave.retry=2

# Threads
thread.pool.size=10

# Paths
temp.dir=tmp
data.dir=data
logs.dir=logs
Usage

At startup:

read properties file

load values

use instead of constants

Benefits

easy deployment

no recompilation needed

environment independent

production ready

Teacher usually expects this feature.

🧠 Final Architecture Summary
Version 1
Client → Threaded Server → Local execution
Version 2
Client → Master → Slaves → Parallel execution
✅ Features Achieved

✅ OOP concept
✅ TCP Client–Server
✅ Multithreading
✅ Thread safety
✅ Map + List + PriorityQueue
✅ Singleton pattern
✅ Strategy pattern
✅ Data persistence
✅ External configuration
✅ Dynamic JAR execution
✅ Distributed processing