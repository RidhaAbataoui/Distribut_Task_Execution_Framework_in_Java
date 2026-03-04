#!/bin/bash
# ══════════════════════════════════════════
#   Start a Slave Node
#   Usage: ./start-slave.sh [port]
#   Default port: 6001
#
#   Examples:
#     ./start-slave.sh        → port 6001
#     ./start-slave.sh 6001   → port 6001
#     ./start-slave.sh 6002   → port 6002
#     ./start-slave.sh 6003   → port 6003
# ══════════════════════════════════════════
cd "$(dirname "$0")"

PORT="${1:-6001}"

echo ""
echo "  Starting Slave Node on port $PORT..."
echo "  (Keep this terminal open)"
echo ""

java -cp build slave.SlaveNode "$PORT"
