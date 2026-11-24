#!/usr/bin/env bash
# Enforces docs/contracts/GIT_CONTRACT.md.
#
#   check.sh message <file>              validate one commit message
#   check.sh range <branch> <base> <head> validate a branch before push / in CI
set -euo pipefail

BRANCH_RE='^(fe|be1|be2|ai1|ai2|shared)/(feat|fix|docs|test|refactor|chore)-[a-z0-9]+(-[a-z0-9]+)*$'
SUBJECT_RE='^(feat|fix|docs|test|refactor|chore)\((fe|be1|be2|ai1|ai2|shared)\)!?: .+'
MAX_SUBJECT=72
MAX_FILE_BYTES=$((5 * 1024 * 1024))

errors=0
fail() { echo "  x $*" >&2; errors=$((errors + 1)); }

check_subject() {
    local subject=$1
    [[ $subject =~ ^(Merge|Revert|fixup!|squash!) ]] && return 0
    [[ $subject =~ $SUBJECT_RE ]] ||
        fail "Commit '$subject' must look like 'feat(ai1): short description'"
    ((${#subject} <= MAX_SUBJECT)) ||
        fail "Commit subject is ${#subject} characters, maximum is $MAX_SUBJECT: '$subject'"
}

owner_of() {
    local path=$1 best='' best_length=-1 role prefix
    while read -r role prefix; do
        [[ -z $role || $role == \#* ]] && continue
        if [[ $path == "$prefix"* && ${#prefix} -gt $best_length ]]; then
            best=$role
            best_length=${#prefix}
        fi
    done < "$map"
    echo "${best:-none}"
}

check_range() {
    local branch=$1 base=$2 head=$3 role path owner size commit

    if [[ ! $branch =~ $BRANCH_RE ]]; then
        fail "Branch '$branch' must look like 'ai1/feat-gesture-smoothing'"
        return
    fi
    role=${branch%%/*}

    while read -r commit; do
        [[ -n $commit ]] && check_subject "$(git log -1 --format=%s "$commit")"
    done < <(git rev-list --no-merges "$base..$head")

    while IFS= read -r -d '' path; do
        owner=$(owner_of "$path")
        case $owner in
            "$role" | shared) ;;
            none) fail "$path has no owner: add it to contracts/ownership.map" ;;
            *) fail "$path belongs to $owner, but this is a $role branch" ;;
        esac

        case $path in
            *.env.example) ;;
            *.env | *.env.* | .env | *.jks | *.keystore | *google-services.json | local.properties | */local.properties)
                fail "$path must never be committed" ;;
        esac

        # Deleted files have no size.
        if size=$(git cat-file -s "$head:$path" 2>/dev/null) && ((size > MAX_FILE_BYTES)); then
            fail "$path is $((size / 1024 / 1024)) MB, maximum is $((MAX_FILE_BYTES / 1024 / 1024)) MB"
        fi
    done < <(git diff --name-only --no-renames -z "$base...$head")

    # Report the line only: the matching text must not be echoed into CI logs.
    if git diff -U0 "$base...$head" -- . ':!*.csv' ':!*.tsv' |
        grep -E '^\+' |
        grep -qE 'gsk_[A-Za-z0-9]{20,}|pcsk_[A-Za-z0-9_]{20,}|re_[A-Za-z0-9]{8}_[A-Za-z0-9]{16,}|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----|eyJ[A-Za-z0-9_-]{20,}\.eyJ[A-Za-z0-9_-]{20,}'; then
        fail "A changed line looks like an API key, JWT or private key"
    fi
}

cd "$(git rev-parse --show-toplevel)"
map=contracts/ownership.map

case ${1:-} in
    message)
        check_subject "$(grep -vE '^#' "$2" | head -n 1)"
        ;;
    range)
        check_range "$2" "$3" "$4"
        ;;
    *)
        echo "usage: check.sh message <file> | range <branch> <base> <head>" >&2
        exit 2
        ;;
esac

if ((errors > 0)); then
    echo "Git contract: $errors problem(s). See docs/contracts/GIT_CONTRACT.md" >&2
    exit 1
fi
