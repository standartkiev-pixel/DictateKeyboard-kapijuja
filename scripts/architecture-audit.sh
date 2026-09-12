#!/usr/bin/env bash
set -euo pipefail

controller="app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt"

# Freeze the current ceiling. New behavior must be placed in focused components, and each
# extraction should lower these limits so the monolith cannot silently grow back.
max_lines=4522
max_bytes=245892

if ! iconv -f UTF-8 -t UTF-8 "$controller" >/dev/null; then
  echo "Architecture audit rejected a non-UTF-8 DictateController.kt." >&2
  exit 1
fi

if perl -0777 -ne 'exit(index($_, "\0") < 0 ? 1 : 0)' "$controller"; then
  echo "Architecture audit rejected a NUL byte in DictateController.kt." >&2
  exit 1
fi

actual_lines="$(wc -l < "$controller")"
actual_bytes="$(wc -c < "$controller")"

if (( actual_lines > max_lines )); then
  echo "DictateController.kt grew to $actual_lines lines (limit: $max_lines)." >&2
  echo "Move the new responsibility into a focused component instead." >&2
  exit 1
fi

if (( actual_bytes > max_bytes )); then
  echo "DictateController.kt grew to $actual_bytes bytes (limit: $max_bytes)." >&2
  echo "Move the new responsibility into a focused component instead." >&2
  exit 1
fi

echo "Architecture audit passed: $actual_lines lines, $actual_bytes bytes."
