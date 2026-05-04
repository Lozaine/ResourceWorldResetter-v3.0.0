# ResourceWorldResetter Migration Guide

## Scope
This guide covers upgrades from v3.x to v4.0.0.

v4.0.0 is a breaking-change release that introduces a unified `/rwr` command tree and a versioned config schema (`configVersion: 4`).

## Breaking Changes in v4.0.0
1. Legacy commands are removed.
2. Config scheduling keys are moved to `schedule.*`.
3. Reset state uses explicit phases and a new persisted state format.

### Old command to new command mapping
| v3 command | v4 command |
|---|---|
| `/rwrgui` | `/rwr gui` |
| `/reloadrwr` | `/rwr reload` |
| `/resetworld` | `/rwr reset now` |
| `/rwrregion` | `/rwr region <enable|disable|list|add|remove|addhere>` |
| `/rwrresume` | `/rwr resume [cancel]` |

## Pre-Upgrade Checklist
1. Stop the server.
2. Back up `plugins/ResourceWorldResetter/`.
3. Back up your resource world folder.
4. Back up any automation scripts that call old commands.
5. Confirm Java and server compatibility:
	- Java 25+
	- Spigot API 26.1+
	- Multiverse-Core 4.3.1+

## Upgrade Procedure (v3 -> v4.0.0)
1. Stop the server.
2. Replace the v3 jar with the v4.0.0 jar in `plugins/`.
3. Start the server.
4. Watch startup logs and confirm migration completed.
5. Confirm `plugins/ResourceWorldResetter/config.v3.backup.yml` was created.
6. Open `plugins/ResourceWorldResetter/config.yml` and verify:
	- `configVersion: 4`
	- schedule keys are in `schedule.mode`, `schedule.time.hour`, `schedule.warningMinutes`, `schedule.day`
7. Update automation and command macros to the new `/rwr` command tree.
8. Run in-game verification commands:
	- `/rwr status`
	- `/rwr next`
	- `/rwr gui`
9. Trigger one manual reset in a low-activity window:
	- `/rwr reset now`

## Post-Upgrade Verification
Use this checklist after first startup.

1. Config migration and backups:
	- `config.v3.backup.yml` exists
	- `config.yml` has `configVersion: 4`
2. Command migration:
	- old commands are not found
	- `/rwr` subcommands work
3. Status visibility:
	- `/rwr status` shows phase and schedule information
	- `/rwr next` shows next reset and warning time
4. Region mode (if used):
	- `/rwr region list` returns expected entries
5. Logs:
	- no migration errors
	- no command registration errors

## Permission Node Changes
v4 uses the `resourceworldresetter.admin` permission for the `/rwr` command tree.

If your permissions plugin previously granted access to old command labels, migrate those groups to grant `resourceworldresetter.admin`.

## Rollback Procedure (v4 -> v3)
If you need to return to v3, use this exact order.

1. Stop the server.
2. Restore the previous v3 jar.
3. Move current v4 config aside:
	- `config.yml` -> `config.v4.yml`
4. Restore the v3 backup config:
	- `config.v3.backup.yml` -> `config.yml`
5. Delete `reset-state.yml` created by v4.
6. Start the server.
7. Verify old commands are available and scheduling is correct.

## Common Migration Problems

### Startup says command not found for old labels
Cause: v4 intentionally removed legacy command labels.
Fix: update scripts to `/rwr` subcommands.

### Migration backup missing
Cause: startup migration did not run fully.
Fix: stop server, restore v3 backup, restart with clean logs and inspect first migration error.

### Unexpected schedule behavior after migration
Cause: old flat keys or invalid values were carried forward.
Fix: verify `schedule.mode`, `schedule.time.hour`, `schedule.warningMinutes`, and `schedule.day` values in `config.yml`.

### Status shows incomplete reset state after restart
Cause: a reset was interrupted.
Fix: run `/rwr resume` to continue or `/rwr resume cancel` to clear pending resume state.

## Recommended Safe Rollout
1. Upgrade a staging server first.
2. Run one full reset cycle manually.
3. Let one scheduled cycle complete.
4. Upgrade production after validation.

## Support
If migration fails and rollback also fails, open an issue with:
1. Startup logs from the first failing boot
2. Redacted `config.yml`
3. Your previous plugin version