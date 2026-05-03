# Migration Guide

## v3 -> v4

ResourceWorldResetter v4 migrates the config schema automatically on startup.

### What changes
- Old scheduling keys are moved into `schedule.*`.
- `configVersion: 4` is written into `config.yml`.
- A backup of the old config is saved as `config.v3.backup.yml`.
- Old commands such as `/rwrgui`, `/reloadrwr`, `/resetworld`, `/rwrregion`, and `/rwrresume` are no longer registered.

### What to do before upgrading
1. Stop the server.
2. Back up the full `plugins/ResourceWorldResetter/` folder.
3. Replace the plugin jar with the v4 build.
4. Start the server and check the console for the migration log.

### What to verify after startup
- `config.v3.backup.yml` exists.
- `config.yml` now contains `configVersion: 4`.
- Your schedule values are under `schedule.mode`, `schedule.time.hour`, `schedule.warningMinutes`, and `schedule.day`.
- Your scripts and permissions use `/rwr` subcommands.

### Rollback
1. Stop the server.
2. Restore the previous v3 jar.
3. Restore your backed up config if needed.
4. Remove the v4 `reset-state.yml` if you need a clean start.

If migration fails, restore the backup and check the console log for the first config warning or error.