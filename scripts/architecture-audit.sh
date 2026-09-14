#!/usr/bin/env bash
set -euo pipefail

controller="app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt"

# Freeze the current ceiling. New behavior must be placed in focused components, and each
# extraction should lower these limits so the monolith cannot silently grow back.
max_lines=4438
max_bytes=241173

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

# Current-context documentation is deliberately task-scoped. Git history and the dated handoffs are
# the archive; these files are working memory for the next developer/AI session and must not turn back
# into append-only project diaries. A new subject should get its own topic file instead of making an
# existing one unbounded.
check_context_file() {
  local path="$1"
  local line_limit="$2"
  local byte_limit="$3"

  if [[ ! -f "$path" ]]; then
    echo "Architecture audit expected context file '$path' but it is missing." >&2
    exit 1
  fi
  if ! iconv -f UTF-8 -t UTF-8 "$path" >/dev/null; then
    echo "Architecture audit rejected non-UTF-8 context file '$path'." >&2
    exit 1
  fi

  local lines bytes
  lines="$(wc -l < "$path")"
  bytes="$(wc -c < "$path")"
  if (( lines > line_limit || bytes > byte_limit )); then
    echo "Context file '$path' grew to $lines lines / $bytes bytes." >&2
    echo "Limit: $line_limit lines / $byte_limit bytes. Split the topic or replace stale history." >&2
    exit 1
  fi
  echo "Context audit passed: $path ($lines lines, $bytes bytes)."
}

check_context_file "NEXT_CHAT.md" 180 14000

# Every focused topic is allowed enough room for real engineering detail, but not enough to become a
# second monolithic handoff. This wildcard automatically applies the rule to future topic files too.
shopt -s nullglob
context_files=(docs/context/*.md)
if (( ${#context_files[@]} == 0 )); then
  echo "Architecture audit found no docs/context/*.md task-context files." >&2
  exit 1
fi
for context_file in "${context_files[@]}"; do
  check_context_file "$context_file" 320 26000
done
