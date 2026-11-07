#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Shirokai-Crates
python3 scripts/setup-cloud.py
source scripts/cloud-env.sh
./gradlew test build projectArchive --console plain
