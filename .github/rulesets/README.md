# Branch rulesets

Branch flow for this repository:

```
feature/*, fix/*, claude/*  --PR-->  dev  --PR (release)-->  main
```

`main` is the released, stable branch and should be the repository's
**default branch**. `dev` is the integration branch every feature PR targets.

The two JSON files here are GitHub ruleset exports. Rulesets live in the
repository settings, not in git, so they have to be imported once by a
repository admin.

## What the rulesets enforce (on both `main` and `dev`)

- No direct pushes: changes go through a pull request.
- Required status checks from `.github/workflows/ci.yml`, and the branch must
  be up to date with its base before merging:
  - `Balance simulator`
  - `Gradle build (Java 8)`
- All review conversations resolved before merging.
- No force pushes, no branch deletion.
- Approvals: 0 required, because a solo maintainer cannot approve their own
  PR. Raise `required_approving_review_count` when there are more maintainers.
- Repository admins may bypass (for emergencies). Remove the entry under
  `bypass_actors` to lock admins in too.

## One-time setup

1. **Default branch** — Settings → General → Default branch → switch to
   `main` (it was left on the old bootstrap branch `claude/busy-allen-1swlf8`).
2. **Import rulesets** — Settings → Rules → Rulesets → New ruleset →
   *Import a ruleset*, then pick `main.json`; repeat for `dev.json`.
3. Optional cleanup: delete the merged branches `claude/busy-allen-1swlf8`,
   `feature/v1-implementation` and `fix/edge-cases`, and turn on
   Settings → General → *Automatically delete head branches*.

Or with the GitHub CLI, from the repository root:

```bash
gh repo edit tidefox/unrest --default-branch main --delete-branch-on-merge
```

```bash
for f in .github/rulesets/main.json .github/rulesets/dev.json; do gh api -X POST repos/tidefox/unrest/rulesets --input "$f"; done
```

The status check names must match the CI job `name:` fields. If you rename a
job in `ci.yml`, update both files and the rulesets in the settings.
