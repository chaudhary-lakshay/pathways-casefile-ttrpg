#!/usr/bin/env bash
# Waits for the in-flight round 1 to finish, fills the gaps, re-probes, then cross-verifies.
# Usage: bash qa-findings/run-all.sh
set -u
cd "$(dirname "$0")/.."

echo "[all] round 1"
bash qa-findings/run-round1.sh

echo "[all] round 1 second pass (fills batches that were refused)"
bash qa-findings/run-round1.sh

echo "[all] re-probing the gateway before cross-verification"
node ~/.claude/qa/qa.mjs probe > qa-findings/probe-round2.txt 2>&1
tail -3 qa-findings/probe-round2.txt

echo "[all] round 2: cross-verification"
bash qa-findings/run-round2.sh
echo "[all] pipeline done"
