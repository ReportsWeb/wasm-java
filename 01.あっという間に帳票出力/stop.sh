#!/usr/bin/env sh
set -eu
sample_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
docker compose -f "$sample_dir/compose.yaml" down
