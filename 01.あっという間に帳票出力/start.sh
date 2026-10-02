#!/usr/bin/env sh
set -eu
sample_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
docker compose -f "$sample_dir/compose.yaml" up -d --build --wait --wait-timeout 180
printf '%s\n' 'http://127.0.0.1:19210/'
