📘 Distributed Task Execution System
Version 1 – Client/Server Execution (Socket + Thread Design)
🎯 Objective

Version 1 implements a basic distributed execution system where:

the client sends a task dynamically

the server executes the task locally

the result is returned to the client

The server does not know any algorithm in advance.

Instead, the computation is provided at runtime as a JAR file.

This makes the system generic, extensible, and reusable.

🧠 Core Idea

Instead of hardcoding algorithms inside the server:

❌ Bad:

if task == matrix → run matrix code
if task == filter → run filter code

✅ Good:

receive jar → execute jar → return result

So the server becomes a:

👉 Remote Program Executor

🏗️ Architecture
Client  ─────────────►  Server
        (jar + options + files)

Server:
   execute locally
   return results

Only:

1 client

1 server

no slaves

🧩 Technologies Used

Java Sockets (TCP)

Threads

ProcessBuilder (run jars)

File I/O

OOP design

🎨 Design Pattern Used
Thread-per-Client Pattern
Problem

Multiple clients may send tasks simultaneously.

If the server handles one client at a time:

❌ blocking
❌ slow
❌ poor scalability

Solution

Create one thread for each client connection.

Flow:

ServerSocket.accept()
      ↓
new ClientHandlerThread(socket).start()

Each client is processed independently.

Benefits

concurrent clients

non-blocking server

scalable

simple to implement

🔁 System Workflow
Step 1 — Client prepares task

User provides:

task JAR

options

input files

Example:

Matrix
java -jar MatrixComputation.jar -product A.txt B.txt
Image filter
java -jar Filters.jar -gray myimage.png
Step 2 — Client sends to server

Client sends:

Task package

jar bytes

options

input files bytes

Important:
👉 send file content, not file path

Step 3 — Server accepts connection

Server:

accept socket
create new thread
handle client
Step 4 — Server saves files

Example temp folder:

/tmp/task_12/
   task.jar
   A.txt
   B.txt
Step 5 — Server executes task

Server runs:

java -jar task.jar options files

Using:

ProcessBuilder

Runtime.exec()

The server does not know the algorithm.

It simply launches the program.

Step 6 — Task produces result

The jar generates:

Result.txt   or   Result.png
Step 7 — Server returns result

Server reads output files and sends them back to client.

Client saves locally.

Done.

📦 Task Definition
What the Client Must Send

Each task contains:

1️⃣ JAR file

Executable program

Must contain:

public static void main(String[] args)
2️⃣ Options

Arguments passed to program

Examples:

-product
-gray
3
3️⃣ Input files

Data to process

Examples:

A.txt
B.txt
image.png
message.txt
📦 Task Package (Conceptual Model)
TaskRequest
   jarBytes
   options[]
   inputFiles[]
🧩 Responsibilities
Client

read jar

read files

send data

receive results

Server

accept connections

create thread

store files

execute jar

send results

Task JAR

Responsible only for:

reading input

processing

writing output

Must NOT contain:

sockets

networking

server logic

📁 Example Scenarios
Example 1 — Matrix Multiplication
Client sends
MatrixComputation.jar
-product
A.txt B.txt
Server runs
java -jar MatrixComputation.jar -product A.txt B.txt
Output
Result.txt
Example 2 — Image Filtering
Client sends
Filters.jar
-gray
myimage.png
Server runs
java -jar Filters.jar -gray myimage.png
Output
Result.png
Example 3 — Encryption
Client sends
Encrypt.jar
3
message.txt
Server runs
java -jar Encrypt.jar 3 message.txt
Output
encrypted.txt