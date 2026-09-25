## Change

Describe the behavior or tooling change, why it is needed, and any operator impact.

## Validation

- [ ] `./mvnw -B -ntp verify`
- [ ] `./mvnw -B -ntp -Pplatform-acceptance verify` for runtime, packaging, or dependency changes
- [ ] `bash scripts/verify-artifact.sh` for packaging changes

## Compatibility

Document configuration, command, API, and packaging changes, or state that there are none.
