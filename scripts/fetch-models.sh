#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p models
gh release download models-v1 \
  -R Sathwik-giddi/IRA \
  -D models \
  --pattern '*.gguf' \
  --clobber

shasum -a 256 models/*.gguf
