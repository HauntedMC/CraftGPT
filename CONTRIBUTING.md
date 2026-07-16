# Contributing

Thanks for taking the time to contribute.

## Before You Start

- Use Java 25 and Maven.
- Make sure you can run a local compile and test pass.
- If your change affects runtime behavior, test it in a Paper environment.

## Setup

```bash
git clone <repo-url>
cd CraftGPT
mvn -q -DskipTests compile
```

## Contribution Workflow

1. Create a branch from `main`.
2. Keep the change focused on one clear problem.
3. Add or update tests with the code change.
4. Run local checks before opening a PR.
5. Update docs when operator behavior or configuration expectations change.

## Local Validation

Minimum checks:

```bash
mvn -q -DskipTests compile
mvn -q test
```

Recommended before merge:

```bash
mvn -B verify
```

## Pull Request Expectations

- Use a clear title and summary.
- Explain what changed and why.
- Call out configuration or migration impact.
- Link related issues where relevant.
- Keep commits readable and review-friendly.

## Coding Principles

- Prefer clear, explicit code over clever shortcuts.
- Keep bootstrap, runtime, and domain boundaries clean.
- Handle malformed runtime state defensively.
- Avoid broad side effects in event handlers.
- Log failures in a way operators can act on.

## Security

Do not report vulnerabilities in public issues.
Follow [SECURITY.md](SECURITY.md) for private reporting.
