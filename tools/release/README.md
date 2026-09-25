# Version updates

From clean, current `main`, run `./tools/release/update-version patch --pr` to prepare, push, and open a reviewed version PR in HauntedMC/CraftGPT. The pinned `gh-haunted-release` extension manages the worktree. Use `--dry-run` to inspect the next version. CI validates the PR; merging a version change publishes Maven packages and creates the GitHub release after verification. No local tag or direct push to `main` is needed.

Install once: `gh extension install HauntedMC/gh-haunted-release --pin v1.0.3`.
