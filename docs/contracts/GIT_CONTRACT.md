# Git contract

Rules everyone follows when pushing and pulling. Hooks check them locally and CI checks them on every pull request.

## Once, after cloning

```bash
scripts/git/setup.sh
```

This turns on the hooks and makes `git pull` rebase. JDK 21 and Android SDK 36 are required.

## Branches

`main` always builds. Nobody pushes to it directly.

```
<role>/<type>-<description>        ai1/feat-gesture-smoothing
```

| Part | Allowed |
|---|---|
| role | `fe` `be1` `be2` `ai1` `ai2` `shared` |
| type | `feat` `fix` `docs` `test` `refactor` `chore` |
| description | lowercase words joined by `-` |

One branch is one task, owned by one person, and lives at most a few days.

## Commits

```
<type>(<role>): <description>      feat(ai1): smooth letters over five frames
```

Subject is 72 characters or fewer. Add `!` before the colon for a breaking change to a contract.

## Pull

Pull **before you start working** and **before you push**:

```bash
scripts/git/pull.sh
```

1. Your working tree must be clean: commit or stash first.
2. Your branch is *rebased* on `origin/main`. Do not merge `main` into your branch.
3. Conflict in a file you own: resolve it, `git add`, then `git rebase --continue`.
4. Conflict in a file you do **not** own: ask its owner before resolving. `git rebase --abort` is always safe.

## Push

```bash
scripts/git/push.sh
```

It pulls, checks this contract, builds and tests your module, then pushes your branch. A push is rejected when:

| Rule | Why |
|---|---|
| Target is `main` | Only pull requests change it |
| Branch is behind `origin/main` | You would be testing old code |
| Branch or commit name is malformed | History stays readable |
| A file belongs to another role | Avoids conflicts; see `contracts/ownership.map` |
| A file has no owner | Somebody must review it |
| A secret, `.env`, keystore or `local.properties` is included | This repository is **public** |
| A file is larger than 5 MB | Models and datasets are downloaded, not committed |

Use `--force-with-lease` only, and only on your own branch. Never use plain `--force`. Never skip hooks with
`--no-verify`: CI will fail anyway.

If you pushed a secret, **revoke it first**, then tell the team. Removing the commit does not make it private again.

## Merge

1. Open a pull request into `main` and fill in the template.
2. CI must pass, with one approval from someone else. A change to a `shared` path needs approval from every
   affected owner.
3. **Squash and merge**; the pull request title follows the commit format. Delete the branch.
4. Tell the team when you changed a shared contract, so they pull.

## Changing somebody else's module

Ask the owner to make the change. If they agree you should do it, use a branch with **their** role and request
their review.

## Changing a contract

Anything in `:contracts`, the public API of `:ai-client`, or an HTTP endpoint is a contract.

1. Add the new version next to the old one.
2. Consumers move over in their own branches.
3. Remove the old version once nobody uses it.

## Repository settings (one admin, once)

Hooks can be bypassed, so GitHub must enforce the rules too. In *Settings > Branches*, protect `main`:
require a pull request with one approval, require the `git-contract` and `build` checks, require branches to be
up to date, and block force pushes. In *Settings > General*, allow squash merging only.
