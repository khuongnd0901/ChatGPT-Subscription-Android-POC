#!/usr/bin/env bash
# Requires GitHub CLI authenticated on YOUR machine. Publishes all source PUBLICLY.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OWNER="${GH_OWNER:-khuongnd0901}"
NAME="${GH_REPO_NAME:-ChatGPT-Subscription-Android-POC}"
cd "$ROOT"
command -v gh >/dev/null || { echo 'Install GitHub CLI: https://cli.github.com/'; exit 1; }
command -v git >/dev/null || { echo 'Install git first.'; exit 1; }
gh auth status >/dev/null || { echo 'Run: gh auth login'; exit 1; }
if gh repo view "$OWNER/$NAME" >/dev/null 2>&1; then
  echo "Repository $OWNER/$NAME already exists. Aborting to avoid overwriting it."
  exit 1
fi
if [[ ! -d .git ]]; then git init -b main; fi
git add README.md AGENTS.md LICENSE .gitignore settings.gradle.kts \
  build.gradle.kts gradle.properties gradlew app scripts docs
if git diff --cached --quiet; then
  echo 'No changes to commit. Did you already commit?'
else
  git commit -m 'feat: SIWC Android native OAuth and Responses streaming POC'
fi
gh repo create "$OWNER/$NAME" --public --source=. --remote=origin --push \
  --description='Open-source Android POC: Sign in with ChatGPT plan and Responses API'
echo "Published: https://github.com/$OWNER/$NAME"
