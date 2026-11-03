#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Build the current MiniAct snapshot without changing version declarations.
set -euo pipefail
miniact_repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export HGTV_VERSION_NAME="${HGTV_VERSION_NAME:-1.2.9.1}"
export HGTV_VERSION_CODE="${HGTV_VERSION_CODE:-67}"
exec bash "$miniact_repo_dir/android/gradlew" -p "$miniact_repo_dir/android" \
  :app:assembleDebug -Pkotlin.compiler.execution.strategy=in-process --max-workers=1 "$@"
