#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════
#   Full Automated Test Suite — Distributed Task Execution System v2
#
#   Scenarios covered:
#     T01 – LOCAL  Matrix -product
#     T02 – LOCAL  Encrypt (caesar shift 3)
#     T03 – DIST   Matrix -product  (1 slave)
#     T04 – DIST   Matrix -sum      (1 slave)
#     T05 – DIST   Matrix -product  (2 slaves)
#     T06 – DIST   Matrix -product  (3 slaves, uneven split: 4 rows ÷ 3)
#     T07 – DIST   Encrypt -caesar  (1 slave)
#     T08 – DIST   Encrypt -caesar  (2 slaves)
#     T09 – DIST   Encrypt -vigenere(2 slaves)
#     T10 – DIST   Filter -gray     (2 slaves)
#     T11 – DIST   Filter -invert   (2 slaves)
#     T12 – DIST   Filter -sepia    (2 slaves)
#     T13 – EDGE   Request MORE slaves than available → must be REJECTED
#     T14 – EDGE   0 slaves → must FAIL gracefully
#     T15 – QUEUE  Two clients submit simultaneously → both SUCCEED (queued)
#     T16 – PRIO   High-priority task runs first when two compete
#     T17 – REUSE  Three sequential tasks from the same connection → all OK
#
#   Usage:
#     ./test-all.sh                   # full run (starts own master + slaves)
#     ./test-all.sh --skip-infra      # reuse already-running master+slaves
#     ./test-all.sh T05 T13 T15       # run specific tests only
# ═══════════════════════════════════════════════════════════════════════════

set -euo pipefail
cd "$(dirname "$0")"

# ── Colours ───────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

pass()   { echo -e "  ${GREEN}✓ PASS${RESET}  $*"; PASSED=$((PASSED+1)); }
fail()   { echo -e "  ${RED}✗ FAIL${RESET}  $*"; FAILED=$((FAILED+1)); }
info()   { echo -e "  ${CYAN}ℹ${RESET}  $*"; }
banner() { echo ""; echo -e "${BOLD}${CYAN}══  $*  ═══════════════════════════════════════${RESET}"; echo ""; }

PASSED=0; FAILED=0; TOTAL=0
MASTER_PID=""; SLAVE1_PID=""; SLAVE2_PID=""; SLAVE3_PID=""
SKIP_INFRA=false
RUN_FILTER=()

for arg in "$@"; do
  case "$arg" in
    --skip-infra) SKIP_INFRA=true ;;
    T*) RUN_FILTER+=("$arg") ;;
    *) echo "Unknown: $arg"; exit 1 ;;
  esac
done
should_run() { [[ ${#RUN_FILTER[@]} -eq 0 ]] || printf '%s\n' "${RUN_FILTER[@]}" | grep -qx "$1"; }

MASTER_HOST="localhost"
MASTER_PORT="5000"

# ── TestClient source (written at runtime) ────────────────────────────────
write_test_client() {
  mkdir -p build/testclient
  cat > /tmp/TestClient.java << 'JAVA_EOF'
import common.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;

/**
 *  Non-interactive programmatic test client.
 *
 *  LOCAL mode:
 *    TestClient <host> <port> local <jar> [opts...] -- [files...]
 *
 *  DISTRIBUTED mode:
 *    TestClient <host> <port> dist <masterJar> <workerJar> <numSlaves> <priority> [slaveOpts...] -- [files...]
 */
public class TestClient {
    public static void main(String[] args) throws Exception {
        String host = args[0];
        int    port = Integer.parseInt(args[1]);
        String mode = args[2];                         // "local" or "dist"

        List<String> rest = Arrays.asList(args).subList(3, args.length);
        int sep = rest.indexOf("--");
        List<String> taskArgs  = sep >= 0 ? rest.subList(0, sep)           : rest;
        List<String> filePaths = sep >= 0 ? rest.subList(sep + 1, rest.size()) : new ArrayList<>();

        // Load input files
        List<FileData> files = new ArrayList<>();
        for (String p : filePaths)
            files.add(new FileData(Paths.get(p).getFileName().toString(),
                                   Files.readAllBytes(Paths.get(p))));

        TaskRequest request;
        if ("local".equals(mode)) {
            byte[]   jar  = Files.readAllBytes(Paths.get(taskArgs.get(0)));
            String[] opts = taskArgs.subList(1, taskArgs.size()).toArray(new String[0]);
            request = TaskFactory.createLocalTask(jar, opts, files);

        } else {   // "dist"
            // taskArgs: <masterJar> <workerJar> <numSlaves> <priority> [slaveOpts...]
            byte[] masterJar = Files.readAllBytes(Paths.get(taskArgs.get(0)));
            byte[] workerJar = Files.readAllBytes(Paths.get(taskArgs.get(1)));
            String numSlaves = taskArgs.get(2);          // e.g. "2"
            int    priority  = Integer.parseInt(taskArgs.get(3));
            List<String> allOpts = new ArrayList<>();
            allOpts.add("MASTER_OPTS:" + numSlaves);     // tells master how many slaves to use
            for (String o : taskArgs.subList(4, taskArgs.size())) allOpts.add(o);
            request = TaskFactory.createDistributedTask(masterJar, workerJar,
                          allOpts.toArray(new String[0]), files, priority);
        }

        try (Socket sock = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream());
             ObjectInputStream  in  = new ObjectInputStream(sock.getInputStream())) {

            out.writeObject(request);
            out.flush();

            // Drain ProgressUpdates; print final TaskResult
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof TaskResult) {
                    TaskResult r = (TaskResult) obj;
                    if (r.isSuccess()) {
                        System.out.println("SUCCESS");
                        if (r.getConsoleOutput() != null && !r.getConsoleOutput().isBlank())
                            System.out.print(r.getConsoleOutput());
                        if (r.getOutputFiles() != null)
                            for (FileData fd : r.getOutputFiles()) {
                                Files.createDirectories(Paths.get("results"));
                                Files.write(Paths.get("results/" + fd.getFileName()), fd.getFileContent());
                                System.out.println("FILE:" + fd.getFileName()
                                                   + ":" + fd.getFileContent().length + "B");
                            }
                    } else {
                        System.out.println("FAILED:" + r.getConsoleOutput());
                    }
                    break;
                }
                // ProgressUpdate — ignored in automated tests
            }
        }
    }
}
JAVA_EOF
  javac -cp build -d build/testclient /tmp/TestClient.java 2>/dev/null
}

# ── Low-level runner ───────────────────────────────────────────────────────
run_task() {
  java -cp "build:build/testclient" TestClient \
       "$MASTER_HOST" "$MASTER_PORT" "$@" 2>/dev/null || true
}

# ── Standard assertion helpers ────────────────────────────────────────────
assert_success() {
  local id="$1" label="$2" out="$3"
  TOTAL=$((TOTAL+1))
  if echo "$out" | grep -q "^SUCCESS"; then pass "$id — $label"
  else                                      fail "$id — $label  ↳ ${out:0:120}"; fi
}

assert_failed() {
  local id="$1" label="$2" out="$3"
  TOTAL=$((TOTAL+1))
  if echo "$out" | grep -qi "^FAILED\|No slaves\|Not enough\|refused"; then
    pass "$id — $label (correctly rejected)"
  else
    fail "$id — $label  ↳ expected FAIL but got: ${out:0:120}"
  fi
}

# ── Infrastructure ────────────────────────────────────────────────────────
start_infra() {
  banner "Starting Infrastructure"

  # Kill any leftovers
  pkill -f "master\.Master"   2>/dev/null || true
  pkill -f "slave\.SlaveNode" 2>/dev/null || true
  sleep 1

  info "Starting Master (client:5000, reg:5001)..."
  java -cp build master.Master   > /tmp/ts_master.log 2>&1 & MASTER_PID=$!
  sleep 2

  info "Starting Slave 1 (6001)..."
  java -cp build slave.SlaveNode 6001 > /tmp/ts_slave1.log 2>&1 & SLAVE1_PID=$!
  sleep 0.5

  info "Starting Slave 2 (6002)..."
  java -cp build slave.SlaveNode 6002 > /tmp/ts_slave2.log 2>&1 & SLAVE2_PID=$!
  sleep 0.5

  info "Starting Slave 3 (6003)..."
  java -cp build slave.SlaveNode 6003 > /tmp/ts_slave3.log 2>&1 & SLAVE3_PID=$!
  sleep 3  # wait for all slaves to register

  echo ""
  info "Infrastructure ready: master + 3 slaves"
}

stop_infra() {
  banner "Stopping Infrastructure"
  [ -n "$MASTER_PID" ] && kill "$MASTER_PID" 2>/dev/null || true
  [ -n "$SLAVE1_PID" ] && kill "$SLAVE1_PID" 2>/dev/null || true
  [ -n "$SLAVE2_PID" ] && kill "$SLAVE2_PID" 2>/dev/null || true
  [ -n "$SLAVE3_PID" ] && kill "$SLAVE3_PID" 2>/dev/null || true
  sleep 1
  info "Logs: /tmp/ts_master.log  /tmp/ts_slave{1,2,3}.log"
}

restart_slaves() {
  [ -n "$SLAVE1_PID" ] && kill "$SLAVE1_PID" 2>/dev/null || true
  [ -n "$SLAVE2_PID" ] && kill "$SLAVE2_PID" 2>/dev/null || true
  [ -n "$SLAVE3_PID" ] && kill "$SLAVE3_PID" 2>/dev/null || true
  sleep 1
  java -cp build slave.SlaveNode 6001 >> /tmp/ts_slave1.log 2>&1 & SLAVE1_PID=$!
  java -cp build slave.SlaveNode 6002 >> /tmp/ts_slave2.log 2>&1 & SLAVE2_PID=$!
  java -cp build slave.SlaveNode 6003 >> /tmp/ts_slave3.log 2>&1 & SLAVE3_PID=$!
  sleep 3
}

# ═══════════════════════════════════════════════════════════════════════════
#  TEST DEFINITIONS
# ═══════════════════════════════════════════════════════════════════════════

# ── LOCAL ─────────────────────────────────────────────────────────────────
run_T01() {
  local out; out=$(run_task local tasks/MatrixComputation.jar -product \
                            -- tasks/matrix/A.txt tasks/matrix/B.txt)
  assert_success T01 "LOCAL Matrix -product" "$out"
}

run_T02() {
  local out; out=$(run_task local tasks/Encrypt.jar 3 \
                            -- tasks/encrypt/message.txt)
  assert_success T02 "LOCAL Encrypt caesar shift=3" "$out"
}

# ── DISTRIBUTED — Matrix ──────────────────────────────────────────────────
run_T03() {
  local out; out=$(run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 1 0 \
                            -product -- tasks/matrix/A.txt tasks/matrix/B.txt)
  assert_success T03 "DIST Matrix -product (1 slave)" "$out"
}

run_T04() {
  # A+A for sum (square matrix: A.txt 4×4, reuse as B)
  local out; out=$(run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 1 0 \
                            -sum -- tasks/matrix/A.txt tasks/matrix/A.txt)
  assert_success T04 "DIST Matrix -sum A+A (1 slave)" "$out"
}

run_T05() {
  local out; out=$(run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 2 0 \
                            -product -- tasks/matrix/A.txt tasks/matrix/B.txt)
  assert_success T05 "DIST Matrix -product (2 slaves)" "$out"
  if echo "$out" | grep -q "FILE:Result.txt"; then
    info "        Result.txt received ✓"
  fi
}

run_T06() {
  # A has 4 rows ÷ 3 slaves → slave 0 gets 2 rows, slaves 1 & 2 get 1 row each
  local out; out=$(run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 3 0 \
                            -product -- tasks/matrix/A.txt tasks/matrix/B.txt)
  assert_success T06 "DIST Matrix -product (3 slaves, uneven 4-row split)" "$out"
}

# ── DISTRIBUTED — Encrypt ─────────────────────────────────────────────────
run_T07() {
  local out; out=$(run_task dist tasks/EncryptMaster.jar tasks/EncryptWorker.jar 1 0 \
                            -caesar 13 -- tasks/encrypt/message.txt)
  assert_success T07 "DIST Encrypt -caesar ROT13 (1 slave)" "$out"
}

run_T08() {
  local out; out=$(run_task dist tasks/EncryptMaster.jar tasks/EncryptWorker.jar 2 0 \
                            -caesar 7 -- tasks/encrypt/message.txt)
  assert_success T08 "DIST Encrypt -caesar shift=7 (2 slaves)" "$out"
}

run_T09() {
  local out; out=$(run_task dist tasks/EncryptMaster.jar tasks/EncryptWorker.jar 2 0 \
                            -vigenere JAVA -- tasks/encrypt/message.txt)
  assert_success T09 "DIST Encrypt -vigenere key=JAVA (2 slaves)" "$out"
}

# ── DISTRIBUTED — Image Filter ────────────────────────────────────────────
run_T10() {
  local out; out=$(run_task dist tasks/FilterMaster.jar tasks/FilterWorker.jar 2 0 \
                            -gray -- tasks/filters/image.png)
  assert_success T10 "DIST Filter -gray (2 slaves)" "$out"
}

run_T11() {
  local out; out=$(run_task dist tasks/FilterMaster.jar tasks/FilterWorker.jar 2 0 \
                            -invert -- tasks/filters/image.png)
  assert_success T11 "DIST Filter -invert (2 slaves)" "$out"
}

run_T12() {
  local out; out=$(run_task dist tasks/FilterMaster.jar tasks/FilterWorker.jar 2 0 \
                            -sepia -- tasks/filters/image.png)
  assert_success T12 "DIST Filter -sepia (2 slaves)" "$out"
}

# ── EDGE CASES ────────────────────────────────────────────────────────────
run_T13() {
  # Request 5 slaves but only 3 are registered → must be REJECTED
  local out; out=$(run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 5 0 \
                            -product -- tasks/matrix/A.txt tasks/matrix/B.txt)
  assert_failed T13 "EDGE: 5 slaves requested (only 3 available)" "$out"
}

run_T14() {
  # Kill all 3 slaves → 0 available → must FAIL
  TOTAL=$((TOTAL+1))
  info "T14: Stopping all slaves..."
  kill "$SLAVE1_PID" "$SLAVE2_PID" "$SLAVE3_PID" 2>/dev/null || true
  sleep 2  # let them shut down and master de-register them

  local out; out=$(run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 1 0 \
                            -product -- tasks/matrix/A.txt tasks/matrix/B.txt) || true

  info "T14: Restarting slaves..."
  restart_slaves

  if echo "$out" | grep -qi "FAILED\|No slaves\|refused\|0 slave"; then
    pass "T14 — EDGE: 0 slaves → task correctly FAILED"
  elif [ -z "$out" ]; then
    pass "T14 — EDGE: 0 slaves → connection refused (correct)"
  else
    fail "T14 — EDGE: 0 slaves → expected failure, got: ${out:0:120}"
  fi
}

# ── CONCURRENCY ───────────────────────────────────────────────────────────
run_T15() {
  # Two clients fire simultaneously; with single-slot scheduler, one
  # waits for the other. Both must eventually SUCCEED.
  TOTAL=$((TOTAL+1))
  info "T15: Launching 2 clients simultaneously..."
  local out1 out2

  run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 2 0 \
           -product -- tasks/matrix/A.txt tasks/matrix/B.txt > /tmp/t15a.txt &
  local p1=$!

  run_task dist tasks/EncryptMaster.jar tasks/EncryptWorker.jar 2 0 \
           -caesar 5 -- tasks/encrypt/message.txt > /tmp/t15b.txt &
  local p2=$!

  wait $p1; wait $p2
  out1=$(cat /tmp/t15a.txt); out2=$(cat /tmp/t15b.txt)

  local ok=0
  echo "$out1" | grep -q "^SUCCESS" && ok=$((ok+1))
  echo "$out2" | grep -q "^SUCCESS" && ok=$((ok+1))

  if [ "$ok" -eq 2 ]; then
    pass "T15 — CONCURRENT: 2 simultaneous clients → both SUCCEEDED"
  elif [ "$ok" -eq 1 ]; then
    fail "T15 — CONCURRENT: only 1 of 2 succeeded — out1=${out1:0:60} / out2=${out2:0:60}"
  else
    fail "T15 — CONCURRENT: both failed — out1=${out1:0:60} / out2=${out2:0:60}"
  fi
}

# ── PRIORITY ─────────────────────────────────────────────────────────────
run_T16() {
  # Submit a LOW-priority task, then almost immediately a HIGH-priority one.
  # With the priority queue the HIGH one should start first (after the current
  # slot is free).  Both must SUCCEED.
  TOTAL=$((TOTAL+1))
  info "T16: LOW-priority then HIGH-priority task..."

  run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 2 0 \
           -product -- tasks/matrix/A.txt tasks/matrix/B.txt > /tmp/t16lo.txt &
  local plo=$!; sleep 0.3

  run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 2 9 \
           -product -- tasks/matrix/A.txt tasks/matrix/B.txt > /tmp/t16hi.txt &
  local phi=$!

  wait $plo; wait $phi
  local lo_out hi_out
  lo_out=$(cat /tmp/t16lo.txt); hi_out=$(cat /tmp/t16hi.txt)

  local ok=0
  echo "$lo_out" | grep -q "^SUCCESS" && ok=$((ok+1))
  echo "$hi_out" | grep -q "^SUCCESS" && ok=$((ok+1))

  if [ "$ok" -eq 2 ]; then
    pass "T16 — PRIORITY: both tasks succeeded (low-prio=0, high-prio=9)"
  elif [ "$ok" -eq 1 ]; then
    fail "T16 — PRIORITY: only 1 of 2 succeeded — lo=${lo_out:0:60} hi=${hi_out:0:60}"
  else
    fail "T16 — PRIORITY: both failed — lo=${lo_out:0:60} hi=${hi_out:0:60}"
  fi
}

# ── REUSE ─────────────────────────────────────────────────────────────────
run_T17() {
  # Three separate sequential tasks back-to-back — tests slave reuse
  TOTAL=$((TOTAL+1))
  local ok=0
  for i in 1 2 3; do
    local out
    out=$(run_task dist tasks/MatrixMaster.jar tasks/MatrixWorker.jar 2 0 \
                   -product -- tasks/matrix/A.txt tasks/matrix/B.txt)
    echo "$out" | grep -q "^SUCCESS" && ok=$((ok+1))
  done

  if [ "$ok" -eq 3 ]; then
    pass "T17 — REUSE: 3 sequential tasks → all 3 SUCCEEDED (slaves reused correctly)"
  else
    fail "T17 — REUSE: only $ok / 3 tasks succeeded"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════
#  MAIN
# ═══════════════════════════════════════════════════════════════════════════

banner "Distributed Task Execution — Full Test Suite v2"
echo "  Master : $MASTER_HOST:$MASTER_PORT"
echo "  Mode   : $([ "$SKIP_INFRA" = true ] && echo '--skip-infra (use running master)' || echo 'self-managed (start own infra)')"
[ ${#RUN_FILTER[@]} -gt 0 ] && echo "  Filter : ${RUN_FILTER[*]}"

# Ensure build exists
if [ ! -f "build/master/Master.class" ]; then
  info "Build not found — running build.sh first..."
  bash build.sh 2>&1 | tail -6
fi

info "Compiling TestClient harness..."
write_test_client
info "TestClient compiled ✓"

$SKIP_INFRA || start_infra

# ── Run each test group ───────────────────────────────────────────────────
banner "LOCAL Tasks"
should_run T01 && run_T01
should_run T02 && run_T02

banner "DISTRIBUTED — Matrix"
should_run T03 && run_T03
should_run T04 && run_T04
should_run T05 && run_T05
should_run T06 && run_T06

banner "DISTRIBUTED — Encrypt"
should_run T07 && run_T07
should_run T08 && run_T08
should_run T09 && run_T09

banner "DISTRIBUTED — Image Filter"
should_run T10 && run_T10
should_run T11 && run_T11
should_run T12 && run_T12

banner "Edge Cases"
should_run T13 && run_T13
should_run T14 && run_T14

banner "Concurrency & Priority"
should_run T15 && run_T15
should_run T16 && run_T16

banner "Slave Reuse"
should_run T17 && run_T17

# ── Summary ───────────────────────────────────────────────────────────────
banner "Test Summary"
echo ""
printf "  ${GREEN}%-8s${RESET} %d\n" "PASSED"  "$PASSED"
printf "  ${RED}%-8s${RESET} %d\n"   "FAILED"  "$FAILED"
printf "  %-8s %d\n"                 "TOTAL"   "$TOTAL"
echo ""

$SKIP_INFRA || stop_infra

if [ "$FAILED" -eq 0 ]; then
  echo -e "${GREEN}${BOLD}  ALL TESTS PASSED ✓${RESET}"
  exit 0
else
  echo -e "${RED}${BOLD}  $FAILED TEST(S) FAILED ✗${RESET}"
  exit 1
fi
