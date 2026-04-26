#!/bin/bash
# ══════════════════════════════════════════════════════
#   Clean Script — Remove runtime-generated files
#   Run this before submitting / zipping the project.
# ══════════════════════════════════════════════════════

cd "$(dirname "$0")"

echo ""
echo "  Cleaning runtime-generated files..."
echo ""

# Remove compiled Java classes (teacher runs ./build.sh to regenerate)
rm -rf build/
echo "  ✓ Removed build/ (compiled .class files)"

# Remove runtime task JSON records
rm -f data/*.json
echo "  ✓ Removed data/*.json (runtime task records)"

# Remove runtime log files
rm -f logs/*.log
echo "  ✓ Removed logs/*.log (runtime progress logs)"

# Remove runtime output files
rm -f results/*
echo "  ✓ Removed results/* (runtime output files)"

# Remove any stray root-level log files
rm -f master.log slave*.log
echo "  ✓ Removed any stray *.log at project root"

echo ""
echo "  ✅ Project is clean and ready to submit."
echo "  The teacher just needs to run: ./build.sh"
echo ""
