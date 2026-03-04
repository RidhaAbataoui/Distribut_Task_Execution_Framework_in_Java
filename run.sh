#!/bin/bash
# ════════════════════════════════════════════════
#   Quick Launcher — Distributed Task System v2
# ════════════════════════════════════════════════
# Usage: ./run.sh [master|slave|client|cli]
# ════════════════════════════════════════════════

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
CP="$PROJECT_DIR/build"

case "$1" in
    master)
        echo "[Launcher] Starting Master on port 5000..."
        java -cp "$CP" master.Master
        ;;

    slave)
        PORT="${2:-6001}"
        echo "[Launcher] Starting Slave on port $PORT..."
        java -cp "$CP" slave.SlaveNode "$PORT"
        ;;

    client)
        shift
        echo "[Launcher] Starting Client (command-line mode)..."
        java -cp "$CP" client.Client "$@"
        ;;

    cli | "")
        echo "[Launcher] Starting Interactive Client..."
        java -cp "$CP" client.ClientCLI
        ;;

    *)
        echo ""
        echo "Usage: ./run.sh [master|slave <port>|client <args>|cli]"
        echo ""
        echo "  ./run.sh master              → Start Master on port 5000"
        echo "  ./run.sh slave 6001          → Start Slave on port 6001"
        echo "  ./run.sh slave 6002          → Start Slave on port 6002"
        echo "  ./run.sh cli                 → Start Interactive Client"
        echo "  ./run.sh client localhost 5000 local tasks/Encrypt.jar 3 -- tasks/message.txt"
        echo ""
        ;;
esac
