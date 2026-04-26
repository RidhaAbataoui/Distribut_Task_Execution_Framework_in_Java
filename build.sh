#!/bin/bash
# ══════════════════════════════════════════════════════════════
#   Build Script — Distributed Task Execution System v2
# ══════════════════════════════════════════════════════════════

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src"
BUILD_DIR="$PROJECT_DIR/build"
TASKS_DIR="$PROJECT_DIR/tasks"

echo "╔══════════════════════════════════════════════════╗"
echo "║   Building Distributed Task Execution v2         ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# ── Step 1: Clean previous build ──
echo "[1/5] Cleaning previous build..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
mkdir -p "$PROJECT_DIR/data"
mkdir -p "$PROJECT_DIR/logs"
mkdir -p "$PROJECT_DIR/results"

# ── Step 2: Compile main source code ──
echo "[2/5] Compiling source code (common, config, strategy, persistence, master, slave, client)..."
find "$SRC_DIR" -name "*.java" > "$BUILD_DIR/sources.txt"
javac -d "$BUILD_DIR" @"$BUILD_DIR/sources.txt"
echo "  ✓ Compiled successfully"

# ── Step 3: Build example task JARs (V1) ──
echo "[3/5] Building V1 example task JARs..."

# Matrix Computation JAR (V1 — local mode)
echo "  → Building MatrixComputation.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/matrix"
javac -d "$BUILD_DIR/tasks_tmp/matrix" "$TASKS_DIR/matrix/MatrixComputation.java"
jar cfm "$TASKS_DIR/MatrixComputation.jar" "$TASKS_DIR/matrix/MANIFEST.MF" -C "$BUILD_DIR/tasks_tmp/matrix" .
echo "    ✓ MatrixComputation.jar"

# Filters JAR
echo "  → Building Filters.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/filters"
javac -d "$BUILD_DIR/tasks_tmp/filters" "$TASKS_DIR/filters/Filters.java"
jar cfm "$TASKS_DIR/Filters.jar" "$TASKS_DIR/filters/MANIFEST.MF" -C "$BUILD_DIR/tasks_tmp/filters" .
echo "    ✓ Filters.jar"

# Encrypt JAR
echo "  → Building Encrypt.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/encrypt"
javac -d "$BUILD_DIR/tasks_tmp/encrypt" "$TASKS_DIR/encrypt/Encrypt.java"
jar cfm "$TASKS_DIR/Encrypt.jar" "$TASKS_DIR/encrypt/MANIFEST.MF" -C "$BUILD_DIR/tasks_tmp/encrypt" .
echo "    ✓ Encrypt.jar"

# ── Step 4: Build V2 distributed task JARs ──
echo "[4/5] Building V2 distributed task JARs..."

# Matrix Master JAR (split/merge)
echo "  → Building MatrixMaster.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/matrix_master"
javac -d "$BUILD_DIR/tasks_tmp/matrix_master" "$TASKS_DIR/matrix/MatrixMaster.java"
jar cfm "$TASKS_DIR/MatrixMaster.jar" "$TASKS_DIR/matrix/MANIFEST_MASTER.MF" -C "$BUILD_DIR/tasks_tmp/matrix_master" .
echo "    ✓ MatrixMaster.jar"

# Matrix Worker JAR (processes chunks)
echo "  → Building MatrixWorker.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/matrix_worker"
javac -d "$BUILD_DIR/tasks_tmp/matrix_worker" "$TASKS_DIR/matrix/MatrixWorker.java"
jar cfm "$TASKS_DIR/MatrixWorker.jar" "$TASKS_DIR/matrix/MANIFEST_WORKER.MF" -C "$BUILD_DIR/tasks_tmp/matrix_worker" .
echo "    ✓ MatrixWorker.jar"

# Filter Master JAR
echo "  → Building FilterMaster.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/filter_master"
javac -d "$BUILD_DIR/tasks_tmp/filter_master" "$TASKS_DIR/filters/FilterMaster.java"
jar cfm "$TASKS_DIR/FilterMaster.jar" "$TASKS_DIR/filters/MANIFEST_MASTER.MF" -C "$BUILD_DIR/tasks_tmp/filter_master" .
echo "    ✓ FilterMaster.jar"

# Filter Worker JAR
echo "  → Building FilterWorker.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/filter_worker"
javac -d "$BUILD_DIR/tasks_tmp/filter_worker" "$TASKS_DIR/filters/FilterWorker.java"
jar cfm "$TASKS_DIR/FilterWorker.jar" "$TASKS_DIR/filters/MANIFEST_WORKER.MF" -C "$BUILD_DIR/tasks_tmp/filter_worker" .
echo "    ✓ FilterWorker.jar"

# Encrypt Master JAR
echo "  → Building EncryptMaster.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/encrypt_master"
javac -d "$BUILD_DIR/tasks_tmp/encrypt_master" "$TASKS_DIR/encrypt/EncryptMaster.java"
jar cfm "$TASKS_DIR/EncryptMaster.jar" "$TASKS_DIR/encrypt/MANIFEST_MASTER.MF" -C "$BUILD_DIR/tasks_tmp/encrypt_master" .
echo "    ✓ EncryptMaster.jar"

# Encrypt Worker JAR
echo "  → Building EncryptWorker.jar..."
mkdir -p "$BUILD_DIR/tasks_tmp/encrypt_worker"
javac -d "$BUILD_DIR/tasks_tmp/encrypt_worker" "$TASKS_DIR/encrypt/EncryptWorker.java"
jar cfm "$TASKS_DIR/EncryptWorker.jar" "$TASKS_DIR/encrypt/MANIFEST_WORKER.MF" -C "$BUILD_DIR/tasks_tmp/encrypt_worker" .
echo "    ✓ EncryptWorker.jar"

# ── Step 5: Cleanup ──
echo "[5/5] Cleanup..."
rm -rf "$BUILD_DIR/tasks_tmp"
rm -f "$BUILD_DIR/sources.txt"

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  BUILD COMPLETE ✓"
echo "══════════════════════════════════════════════════════════════"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  HOW TO RUN:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  ▸ LOCAL mode (V1 — Master executes locally):"
echo ""
echo "    Terminal 1 — Start Master:"
    echo "      java -cp build master.Master"
echo ""
echo "    Terminal 2 — Run Client:"
echo "      java -cp build client.Client localhost 5000 local tasks/Encrypt.jar 3 -- tasks/message.txt"
echo "      java -cp build client.Client localhost 5000 local tasks/MatrixComputation.jar -product -- tasks/A.txt tasks/B.txt"
echo ""
echo "  ▸ DISTRIBUTED mode (V2 — Master + Slaves):"
echo ""
echo "    Terminal 1 — Start Slave 1:"
echo "      java -cp build slave.SlaveNode 6001"
echo ""
echo "    Terminal 2 — Start Slave 2:"
echo "      java -cp build slave.SlaveNode 6002"
echo ""
echo "    Terminal 3 — Start Master:"
echo "      java -cp build master.Master"
echo ""
echo "    Terminal 4 — Run Client:"
echo "      java -cp build client.Client localhost 5000 distributed tasks/MatrixMaster.jar tasks/MatrixWorker.jar -product -- tasks/A.txt tasks/B.txt"
echo "      java -cp build client.Client localhost 5000 distributed tasks/FilterMaster.jar tasks/FilterWorker.jar -gray -- tasks/filters/image.png"
echo ""
echo ""
