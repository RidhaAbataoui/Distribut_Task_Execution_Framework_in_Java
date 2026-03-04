#!/bin/bash
# ══════════════════════════════════════════
#   Start the Master (Server)
#   Run this FIRST before any slaves/clients
# ══════════════════════════════════════════
cd "$(dirname "$0")"

echo ""
echo "  Starting Master Node..."
echo "  (Keep this terminal open)"
echo ""

java -cp build master.Master
