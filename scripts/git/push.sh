#!/usr/bin/env bash
# PUSH contract: sync, test what you own, push your branch.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

branch=$(git symbolic-ref --quiet --short HEAD) || { echo "Not on a branch." >&2; exit 1; }
[[ $branch != main ]] || { echo "Never push to main; open a pull request." >&2; exit 1; }

scripts/git/pull.sh
scripts/git/check.sh range "$branch" origin/main HEAD

case ${branch%%/*} in
    fe) tasks=(:app:assembleDebug) ;;
    ai1) tasks=(:ai-client:testDebugUnitTest :app:assembleDebug) ;;
    be1) tasks=(:backend:server:build :backend:platform-core:build) ;;
    be2) tasks=(:backend:realtime-chat:build) ;;
    ai2) tasks=(:backend:rag-llm:build) ;;
    shared) tasks=(build) ;;
esac
./gradlew "${tasks[@]}"

# The rebase rewrites your own commits; the lease refuses if someone else pushed meanwhile.
git push --force-with-lease --set-upstream origin "$branch"
echo "Pushed. Open a pull request into main."
