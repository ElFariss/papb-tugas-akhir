#!/usr/bin/env bash
# PULL contract: bring the current branch up to date with origin/main by rebasing.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

branch=$(git symbolic-ref --quiet --short HEAD) || { echo "Not on a branch." >&2; exit 1; }
if [[ -n $(git status --porcelain --untracked-files=no) ]]; then
    echo "Commit or stash your changes first." >&2
    exit 1
fi

git fetch --prune origin
if [[ $branch == main ]]; then
    git merge --ff-only origin/main
else
    # Someone else (or your other laptop) may have pushed to this branch.
    if git rev-parse --verify --quiet "refs/remotes/origin/$branch" > /dev/null; then
        remote_only=$(git rev-list --count --cherry-pick --right-only "HEAD...origin/$branch")
        if ((remote_only > 0)); then git rebase "origin/$branch"; fi
    fi
    git rebase origin/main
fi
echo "'$branch' is up to date with origin/main."
