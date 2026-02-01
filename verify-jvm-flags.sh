#!/bin/bash
# Verify JVM flags are applied correctly
# Run this AFTER restarting VS Code and opening this project

echo "=== Checking Java Language Server Process ==="
echo ""

# Find the JDT language server process
jps -v | grep "org.eclipse.equinox.launcher" | head -1 | while read -r line; do
    echo "Found Language Server:"
    echo "$line"
    echo ""

    echo "=== Checking for problematic flags (should NOT appear) ==="
    echo "$line" | grep -q "XX:+AutoCreateSharedArchive" && echo "❌ FAIL: AutoCreateSharedArchive is ENABLED (bad)" || echo "✅ PASS: AutoCreateSharedArchive not found"
    echo "$line" | grep -q "XX:+AllowArchivingWithJavaAgent" && echo "❌ FAIL: AllowArchivingWithJavaAgent is ENABLED (bad)" || echo "✅ PASS: AllowArchivingWithJavaAgent not found"
    echo "$line" | grep -q "SharedArchiveFile=.*jdtls.jsa" && echo "❌ FAIL: SharedArchiveFile pointing to .jsa (bad)" || echo "✅ PASS: No .jsa SharedArchiveFile"
    echo "$line" | grep -q "XX:+UseParallelGC" && echo "❌ FAIL: Using ParallelGC (bad)" || echo "✅ PASS: Not using ParallelGC"
    echo ""

    echo "=== Checking for good flags (should appear) ==="
    echo "$line" | grep -q "XX:+UseG1GC" && echo "✅ PASS: Using G1GC" || echo "❌ FAIL: Not using G1GC"
    echo "$line" | grep -q "Xshare:off" && echo "✅ PASS: CDS disabled with Xshare:off" || echo "❌ FAIL: Xshare:off not found"
    echo "$line" | grep -q "Xmx4G" && echo "✅ PASS: Max heap is 4G" || echo "⚠️  WARNING: Max heap is not 4G"
    echo ""
done

echo "=== Instructions ==="
echo "If you see ❌ FAIL marks, restart VS Code and try again."
echo "All checks should be ✅ PASS after the settings take effect."
