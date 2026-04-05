#!/bin/sh
set -e

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_DIR"

REMOTE="${REMOTE:-origin}"
BRANCH="${BRANCH:-main}"

# Stage generated artefacts (ignore errors if paths missing)
if ! git add -A src/test/resources/features/generated src/test/resources/datasets 2>/dev/null; then
  git add -A src/test/resources/features/generated 2>/dev/null || true
fi

# Exit quietly if nothing changed
if git diff --cached --quiet; then
  exit 0
fi

# Ensure branch exists locally
if ! git rev-parse --verify "$BRANCH" >/dev/null 2>&1; then
  git checkout -B "$BRANCH"
fi

# Ensure author info is set (fallback machine identity)
if ! git config user.name >/dev/null 2>&1; then
  git config user.name "api-test-generator"
fi
if ! git config user.email >/dev/null 2>&1; then
  git config user.email "automation@example.com"
fi

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S %z')
COMMIT_MSG="chore: update generated features ($TIMESTAMP)"

git commit -m "$COMMIT_MSG"

git push "$REMOTE" "$BRANCH"
