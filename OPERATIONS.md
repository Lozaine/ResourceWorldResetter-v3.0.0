# Operations Guide

## Daily checks
- Use `/rwr status` to confirm the current reset phase, next reset time, and any active warnings.
- Use `/rwr next` to verify the next reset and warning timestamps in server time.
- Review console logs for migration messages, failed reset phases, and any resume activity after restart.

## Reset recovery
- If a reset is interrupted, check `/rwr status` before restarting anything manually.
- Follow the phase reported by status to decide whether the world is teleporting, unloading, deleting, or recreating.
- If the reset is stuck, inspect the last failure reason in the log and resume only after the root cause is addressed.

## Migration handling
- On first boot after upgrade, ResourceWorldResetter writes `configVersion: 4` and creates `config.v3.backup.yml`.
- Keep the backup until you have verified a successful scheduled reset.
- If you need to roll back, restore the previous jar and put the backup config back in place.

## Log interpretation
- `Migration completed` means the config schema was upgraded successfully.
- `reset in progress` indicates the plugin is still working through the reset flow.
- Any exception stack trace during reset should be treated as a blocking issue until the underlying cause is fixed.

## Support notes
- Keep the server stopped while replacing jars or restoring config backups.
- After any upgrade, verify `/rwr status`, `/rwr next`, and the latest console log before opening the world to players.
- If you see unexpected behavior, capture the log around startup and the full reset lifecycle before retrying.
