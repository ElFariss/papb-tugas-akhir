#!/usr/bin/env bash
# Run once after cloning.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
git config pull.rebase true
git config rebase.autoStash true
git config fetch.prune true
# A branch created from origin/main must not track main, or a plain "git push" would target it.
git config branch.autoSetupMerge simple
git config push.autoSetupRemote true
echo "Hooks enabled; 'git pull' now rebases."
