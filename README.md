# ResourceWorldResetter v4.0.0

<p align="center">
  <img src="https://files.catbox.moe/xhfveh.png" alt="project-image">
</p>

## Overview
ResourceWorldResetter v4 automates resource-world resets on a Minecraft server with **deterministic state machine resets**, **preflight safety gates**, and an **admin GUI**. It integrates with **Multiverse-Core** to handle world regeneration without server restarts and now includes a new unified `/rwr` command structure with comprehensive operator visibility.

**⚠️ v4.0.0 is a major release with breaking changes.** See [Breaking Changes](#breaking-changes) and [Migration Guide](#migration-from-v3) below.

## Key Features

### ✨ Core Functionality
- **Automated world resets** (daily, weekly, or monthly schedules)
- **Selective region resets** — reset specific in-world regions without full world deletion
- **Multiverse-Core** v4.3.1+ integration
- **GUI-based configuration** — no manual YAML editing required

### 🛡️ Safety & Reliability (v4 Enhancements)
- **Deterministic reset state machine** — resets persist through server restarts and resume from the last safe phase
- **Preflight safety gates** — configurable TPS, player count, and disk space thresholds prevent bad resets
- **Reset failure recovery** — automatic retry with exponential backoff; detailed failure reporting
- **Graceful shutdown recovery** — incomplete resets auto-resume 60s after server restart (admins can override via `/rwr resume`)
- **Safe player teleportation** — guarantees solid ground landing; automatic fallback if no safe spot exists

### 📊 Operator Visibility (v4 Enhancements)
- `/rwr status` — shows exact reset phase, next reset time, preflight gate status, and last failure reason
- `/rwr next` — displays next scheduled reset in server timezone
- Phase-level detail reporting: know exactly where in the reset pipeline things stand

### 🎮 Player Experience
- **World teleport GUI** — `/rwr tp` opens a menu to select and teleport to any world; `/rwr back` returns to previous location
- Configurable reset warnings (countdown broadcasts before reset)
- **Region throttling** — batch chunk unloading (16/tick) to prevent main-thread blocking
- **Custom events** — `PreResetEvent`, `PostResetEvent`, `RegionPreResetEvent`, `RegionPostResetEvent` for plugin integration

### ⚙️ Technical
- **Async reset operations** — non-blocking world deletion and recreation
- Requires **Java 21 or newer** and **Spigot API 26.1+** (tested on 26.1, 26.1.1, 26.1.2)
- Config validation with automatic error recovery
- Optional analytics integration

## Installation

### Prerequisites
- **Multiverse-Core** 4.3.1 or later (required)
- **Java 21 or newer**
- **Spigot 1.20.5+** (API 26.1+)

### Steps
1. **Download** the latest v4 release from [GitHub Releases](https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/releases).
2. **Back up** your worlds, plugins folder, and server configuration.
3. Place `ResourceWorldResetter.jar` into your `plugins/` folder.
4. Restart your server. On first startup, the plugin will:
   - Create a default `config.yml` with v4 schema
   - If you are upgrading from v3, auto-migrate your old config to v4 format and create a backup (`config.v3.backup.yml`)
5. Create or select the world you want to use as your resource world (e.g., `/mv create Resources normal`).
6. Run `/rwr gui` and click **Change World** to select it.
7. Configure your reset schedule, preflight gates, and region settings in the GUI or `config.yml`.

## Breaking Changes ⚠️

### v4.0.0 introduces one major breaking change:
**All old commands (`/rwrgui`, `/reloadrwr`, `/resetworld`, `/rwrregion`, `/rwrresume`) are no longer registered.**

You must update any automation, scripts, cron jobs, and permission configurations to use the new `/rwr` subcommand structure (see [Commands](#commands--permissions) below).

### What is automatic:
- Config is auto-migrated from v3 schema to v4 on first startup
- A backup of your old config is created as `config.v3.backup.yml`
- Old v3 reset-state format is archived if found

### What admins must verify:
1. Update all scripts and automation to use `/rwr` subcommands
2. Update permission nodes if you have custom permission groups
3. Run `/rwr status` and confirm expected world, schedule, and next reset time
4. Review and tune **preflight gate settings** (TPS, players, disk thresholds)
5. Test a manual reset during low activity: `/rwr reset now`

## Commands & Permissions

| Command | Description | Permission |
|---------|-------------|-----------|
| `/rwr gui` | Open the admin configuration GUI | `resourceworldresetter.admin` |
| `/rwr reload` | Reload config from file | `resourceworldresetter.admin` |
| `/rwr reset now` | Force an immediate reset | `resourceworldresetter.admin` |
| `/rwr resume [cancel]` | Resume or cancel incomplete reset recovery | `resourceworldresetter.admin` |
| `/rwr status` | Show current phase, next reset, gate status | `resourceworldresetter.admin` |
| `/rwr next` | Show next scheduled reset time | `resourceworldresetter.admin` |
| `/rwr region <enable\|disable\|list\|add\|remove\|addhere>` | Manage region-based resets | `resourceworldresetter.admin` |
| `/rwr tp` | Open world teleport menu | `resourceworldresetter.admin` |
| `/rwr back` | Teleport back to previous location | `resourceworldresetter.admin` |

## Configuration (v4 Schema)

### Example config.yml (v4 format)
```yaml
configVersion: 4
worldName: "Resources"

schedule:
  mode: "daily"         # Options: daily, weekly, monthly
  time:
    hour: 3             # 24-hour format (0-23)
  warningMinutes: 5
  day: 1                # For weekly: 1-7 (Mon-Sun); For monthly: 1-31

preflight:
  tps:
    min: 15.0           # Abort if TPS drops below this (0 = disabled)
    onFail: "delay"     # Options: delay, abort, force
  players:
    max: 0              # Abort if more players online (0 = disabled)
    onFail: "delay"
  disk:
    freeGB: 5.0         # Abort if free disk space below this (0 = disabled)
    onFail: "delay"

regions:
  enabled: false        # Enable selective region resets
  # regions will be populated via /rwr region add

metrics:
  enabled: false        # Optional: send anonymous usage data
```

### Key changes from v3:
- Old flat keys (`resetWarningTime`, `restartTime`, `resetType`, etc.) are now under `schedule.*`
- New `preflight.*` section for safety gates (TPS, players, disk)
- `configVersion: 4` marks the schema version
- Auto-migrated configs are valid; no manual changes needed

## Reset State Machine (v4 Enhancement)

The plugin now uses an explicit state machine for all resets:

```
IDLE → PRECHECK → TELEPORT → UNLOAD → DELETE → RECREATE → VERIFY → COMPLETE
                      ↓ (on failure)
                    FAILED → (resume logic or manual recovery)
```

- **Phase-specific error handling**: If a reset fails at any phase, the exact failure is logged and saved
- **Deterministic resume**: On next server start, the reset resumes from the last safe phase, not from scratch
- `/rwr status` reports the exact current phase, not just "IN_PROGRESS"
- Admins can retry, abort, or force recovery via GUI or commands

## Preflight Gates (v4 Enhancement)

Resets are now blocked if:
- **TPS drops below threshold** (default: 15.0) — e.g., during mob farming events
- **Player count exceeds limit** (default: disabled) — e.g., to protect active play sessions
- **Free disk space falls below threshold** (default: 5.0 GB) — to prevent "out of disk" resets

Each gate is configurable: `delay` (retry after N minutes), `abort` (skip reset), or `force` (dangerous).

Use `/rwr status` to see gate status and why a reset was blocked.

## Migration from v3

See **[MIGRATION.md](MIGRATION.md)** for a complete step-by-step upgrade guide, including:
- Automated config migration
- Command syntax migration
- Testing procedures
- Rollback steps

### Quick summary:
1. Back up your plugin folder and worlds
2. Replace the old jar with v4
3. Restart server; config will auto-migrate
4. Update automation scripts to use `/rwr` subcommands
5. Review preflight gate settings: `/rwr status`
6. Test with `/rwr reset now` during low activity

## Rollback Procedure

If you need to return to v3:
1. Stop server immediately if reset is in-progress
2. Restore v3 jar from backup
3. Rename `config.yml` to `config.v4.yml`, then rename `config.v3.backup.yml` to `config.yml`
4. Delete the v4 `reset-state.yml` file
5. Start server and verify old commands work

For full details, see **[MIGRATION.md](MIGRATION.md)**.

## Troubleshooting

### "Command not found" for old commands
**Cause**: v4 does not register `/rwrgui`, `/resetworld`, `/rwrregion`, etc.  
**Solution**: Update your scripts to use `/rwr gui`, `/rwr reset now`, `/rwr region list`, etc. See [Commands](#commands--permissions).

### Reset is blocked by preflight gates
**Cause**: TPS, player count, or disk space threshold was breached.  
**Solution**: Run `/rwr status` to see which gate blocked it. Tune thresholds in `config.yml` under `preflight.*` or wait for conditions to improve.

### Incomplete reset not resuming
**Cause**: Admin ran `/rwr resume cancel` or reset failed after 3 retries.  
**Solution**: Check logs for failure reason. Run `/rwr reset now` to retry, or run `/rwr resume` to re-enable auto-resume.

### Config migration warnings in logs
**Cause**: Old v3 config keys that could not be auto-mapped.  
**Solution**: Review the migration log, check `config.v3.backup.yml` for missing values, and update `config.yml` manually. Most defaults are safe.

### Reset hangs or times out
**Cause**: Large world, slow disk, or server stuttering.  
**Solution**: Monitor `/rwr status` to see which phase is stuck. Check server logs for errors. Increase phase timeouts or disable region throttling in advanced config (if applicable).

## Support & Contribution

- **Bug Reports**: [GitHub Issues](https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/issues)
- **Discord**: [LozDev Mines](https://discord.gg/Y3UuG7xu9x)
- **Contributing**: Pull requests welcome!

## Author & License

**Author**: Lozaine  
GitHub: [Lozaine](https://github.com/Lozaine)  
Discord: [LozDev Mines](https://discord.gg/Y3UuG7xu9x)  

Licensed under the [MIT License](LICENSE).

---

**For comprehensive documentation, configuration examples, and advanced usage**, see:
- **[MIGRATION.md](MIGRATION.md)** — v3 → v4 upgrade guide
- **[OPERATIONS.md](OPERATIONS.md)** — operator guide and troubleshooting
