# ResourceWorldResetter v4.0.0

<p align="center">
  <img src="https://files.catbox.moe/i656bi.png" alt="project-image">
</p>

## Overview
ResourceWorldResetter v4 automates resource-world resets on a Minecraft server with an explicit reset phase model, an **admin GUI**, and a unified `/rwr` command structure. It integrates with **Multiverse-Core** to handle world regeneration without server restarts and improves operator visibility with phase-aware status commands.

**⚠️ v4.0.0 is a major release with breaking changes.** See [Breaking Changes](#breaking-changes) and [Migration Guide](#migration-from-v3) below.

> Scope note: v4.0.0 focuses on schema/command breaking changes and phase visibility. Production hardening features such as advanced preflight gating policy and deterministic resume hardening are targeted for v4.1.0.

## ⚠️ Breaking Changes — READ THIS FIRST

### v4.0.0 introduces one major breaking change:
**All old commands (`/rwrgui`, `/reloadrwr`, `/resetworld`, `/rwrregion`, `/rwrresume`) are no longer registered.**

You must update any automation, scripts, cron jobs, and permission configurations to use the new `/rwr` subcommand structure.

### Old command → New command mapping
| v3 command | v4 command | Purpose |
|---|---|---|
| `/rwrgui` | `/rwr gui` | Open admin configuration GUI |
| `/reloadrwr` | `/rwr reload` | Reload config from file |
| `/resetworld` | `/rwr reset now` | Force immediate reset |
| `/rwrregion` | `/rwr region <enable\|disable\|list\|add\|remove\|addhere>` | Manage region resets |
| `/rwrresume` | `/rwr resume [cancel]` | Resume or cancel incomplete reset |

### What is automatic:
- Config is auto-migrated from v3 schema to v4 on first startup
- A backup of your old config is created as `config.v3.backup.yml`
- Old v3 reset-state format is archived if found

### What admins must verify:
1. Update all scripts and automation to use `/rwr` subcommands
2. Update permission nodes if you have custom permission groups
3. Run `/rwr status` and confirm expected world, schedule, and next reset time
4. Test a manual reset during low activity: `/rwr reset now`

> **For detailed upgrade instructions, see [MIGRATION.md](MIGRATION.md)**

## Key Features

### ✨ Core Functionality
- **Automated world resets** (daily, weekly, or monthly schedules)
- **Selective region resets** — reset specific in-world regions without full world deletion
- **Multiverse-Core** v4.3.1+ integration
- **GUI-based configuration** — no manual YAML editing required

### 🛡️ Safety & Reliability (v4 Foundation)
- **Phase-aware reset state model** — reset progress is tracked by explicit phases
- **Reset state persistence** — current phase and metadata are saved for operator visibility
- **Reset failure tracking** — failure phase and reason are exposed via status output
- **Manual recovery control** — `/rwr resume` and `/rwr resume cancel` for incomplete reset handling
- **Safe player teleportation** — guarantees solid ground landing; automatic fallback if no safe spot exists

### 📊 Operator Visibility (v4 Enhancements)
- `/rwr status` — shows exact reset phase, next reset time, and last failure reason
- `/rwr next` — displays next scheduled reset in server timezone
- Phase-level detail reporting: know exactly where in the reset pipeline things stand

### 🧭 Admin GUI (`/rwr gui`)

Display and change core plugin settings quickly using the in-game Admin GUI. Click the icons to open the corresponding settings page (world selector, schedule, warning time, regions, force reset, reload config, etc.).

![Admin GUI](https://files.catbox.moe/we4uve.png)


### 🎮 Player Experience
- **World teleport GUI** — `/rwr tp` opens a menu to select and teleport to any world; `/rwr back` returns to previous location
- Configurable reset warnings (countdown broadcasts before reset)
- **Region throttling** — batch chunk unloading (16/tick) to prevent main-thread blocking
- **Custom events** — `PreResetEvent`, `PostResetEvent`, `RegionPreResetEvent`, `RegionPostResetEvent` for plugin integration

### ⚙️ Technical
- **Async reset operations** — non-blocking world deletion and recreation
- Requires **Java 25 or newer** and **Spigot API 26.1+** (tested on 26.1, 26.1.1, 26.1.2)
- Config validation with automatic error recovery
- Optional analytics integration

## Installation

### Prerequisites
- **Multiverse-Core** 4.3.1 or later (required)
- **Java 25 or newer**
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

## Commands & Permissions

| Command | Description | Permission |
|---------|-------------|-----------|
| `/rwr gui` | Open the admin configuration GUI | `resourceworldresetter.admin` |
| `/rwr reload` | Reload config from file | `resourceworldresetter.admin` |
| `/rwr reset now` | Force an immediate reset | `resourceworldresetter.admin` |
| `/rwr resume [cancel]` | Resume or cancel incomplete reset recovery | `resourceworldresetter.admin` |
| `/rwr status` | Show current phase, next reset, and failure/resume details | `resourceworldresetter.admin` |
| `/rwr next` | Show next reset timestamp, warning timestamp, and countdown (server timezone) | `resourceworldresetter.admin` |
| `/rwr region <enable\|disable\|list\|add\|remove\|addhere>` | Manage region-based resets | `resourceworldresetter.admin` |
| `/rwr tp` | Open world teleport menu | `resourceworldresetter.admin` |
| `/rwr back` | Teleport back to previous location | `resourceworldresetter.admin` |

## Configuration (v4 Schema)

In-game help: Use `/rwr help` in-game to see a complete, command-by-command help page including usage examples and permission hints.


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

- **Phase-specific error handling**: If a reset fails at any phase, the exact failure is logged and saved with duration metadata
- **Deterministic resume**: On next server start, the reset resumes from the last persisted safe phase boundary, not from scratch
- **Transition hooks**: Enter/exit hooks persist the next resume phase so restart recovery does not guess where the flow stopped
- `/rwr status` reports the exact current phase, not just "IN_PROGRESS"
- Admins can retry, abort, or force recovery via GUI or commands

## Preflight Gates (v4 Enhancement)

_Planned for v4.1.0 production hardening. Included here for forward-looking configuration context._

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

### Migration & Upgrade Issues

#### "Command not found" for old commands
**Cause**: v4 does not register `/rwrgui`, `/resetworld`, `/rwrregion`, etc.  
**Solution**: Update your scripts to use `/rwr gui`, `/rwr reset now`, `/rwr region list`, etc. See [Commands](#commands--permissions) and [Breaking Changes](#-breaking-changes--read-this-first).

#### Migration backup missing after startup
**Cause**: The startup migration did not run fully (e.g., permission error, disk space).  
**Solution**: 
1. Stop server and check the startup log for migration errors
2. Manually verify `config.v3.backup.yml` exists
3. If missing, restore v3 jar, check permissions, and retry startup

#### "Migration failed" errors in logs
**Cause**: Old v3 config contains invalid keys or incompatible values.  
**Solution**: 
1. Review the full startup log for which keys failed
2. Check `config.v3.backup.yml` for the original values
3. Manually update `config.yml` with safe values
4. Refer to the [Configuration (v4 Schema)](#configuration-v4-schema) section for valid key formats

#### Unexpected schedule behavior after migration
**Cause**: Old flat keys or invalid values were not properly converted.  
**Solution**: Verify these config keys in `config.yml`:
   - `schedule.mode` must be `daily`, `weekly`, or `monthly`
   - `schedule.time.hour` must be 0-23
   - `schedule.day` must be 1-7 (weekly) or 1-31 (monthly)
   - `schedule.warningMinutes` must be a positive number

### Configuration Validation Issues

#### Plugin fails to load with config errors
**Cause**: `config.yml` contains invalid YAML syntax or missing required keys.  
**Solution**:
   1. Check the startup log for the specific validation error
   2. Use a YAML validator: https://www.yamllint.com/
   3. Ensure all required fields exist: `configVersion`, `worldName`, `schedule`
   4. Verify indentation (2 spaces, no tabs)

#### "worldName not found" error
**Cause**: The configured world does not exist on the server.  
**Solution**: 
   1. Run `/mv list` to see all available worlds
   2. Create the resource world if needed: `/mv create ResourceName normal`
   3. Update `config.yml` with the correct world name
   4. Run `/rwr reload`

#### "Invalid schedule mode" or "Invalid schedule day" errors
**Cause**: Config contains invalid values for schedule fields.  
**Solution**: 
   1. For `schedule.mode`: Use only `daily`, `weekly`, or `monthly` (lowercase)
   2. For `schedule.day`: Use 1-7 for weekly (1=Monday, 7=Sunday) or 1-31 for monthly (1=1st of month)
   3. Correct the values in `config.yml` and run `/rwr reload`

### Runtime & Operational Issues

#### `/rwr status` reports unexpected phase after restart
**Cause**: A reset may have been interrupted and persisted as incomplete.  
**Solution**: Run `/rwr resume` to continue recovery, or `/rwr resume cancel` to clear the incomplete state.

#### Incomplete reset not resuming
**Cause**: Admin ran `/rwr resume cancel` or reset failed after 3 retries.  
**Solution**: Check logs for failure reason. Run `/rwr reset now` to retry, or run `/rwr resume` to re-enable auto-resume.

#### Reset hangs or times out
**Cause**: Large world, slow disk, or server stuttering.  
**Solution**: Monitor `/rwr status` to see which phase is stuck. Check server logs for errors. Consider:
   - Reducing world size
   - Disabling region throttling (advanced config)
   - Increasing available server RAM
   - Running resets during lower-activity windows

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
