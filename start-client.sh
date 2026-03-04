#!/bin/bash
# ══════════════════════════════════════════
#   Start the Interactive Client
#   Connects automatically to the Master
#   You can run this in MULTIPLE terminals!
# ══════════════════════════════════════════
cd "$(dirname "$0")"

echo ""
echo "  Starting Interactive Client..."
echo "  Connecting to server from config/config.properties"
echo ""

java -cp build client.ClientCLI
