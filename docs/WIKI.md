# ResourceWorldResetter v4 Wiki

## Table of Contents
- Overview
- What Changed in v4
- Key Features
- Requirements
- Installation
- Upgrade from v3
- Quick Start
- Commands
- Permissions
- Configuration Reference
- Scheduling Behavior
- Reset Lifecycle and Phases
- Region Reset Mode
- Teleport and Back System
- Admin GUI Guide
- Events for Plugin Developers
- Operations Runbook
- Troubleshooting
- FAQ

---

## Overview
ResourceWorldResetter is a Spigot plugin that automates resource-world resets with a unified command tree under /rwr, GUI-based administration, and phase-aware reset status.

v4 introduces a state-machine reset flow, persistent reset-state tracking, and migration from older config/command formats.

---

## What Changed in v4

### Breaking changes
- Legacy standalone commands are removed.
- All operations are now under /rwr subcommands.
- Config moved to schema version 4 with schedule keys grouped under schedule.
- Reset progress uses explicit phases and persists state in reset-state.yml for resume/recovery.

### Old to new command mapping
| v3 Command | v4 Command |
|---|---|
| /rwrgui | /rwr gui |
| /reloadrwr | /rwr reload |
| /resetworld | /rwr reset now |
| /rwrregion | /rwr region enable\|disable\|list\|add\|remove\|addhere |
| /rwrresume | /rwr resume [cancel] |

---

## Key Features
- Automated full world resets (daily, weekly, monthly).
- Region-based resets by region coordinate (x,z region file coordinates).
- Unified /rwr command system with tab completion.
- Admin GUI for world selection and schedule settings.
- Player teleport GUI with safe-location landing.
- Back teleport support with reset-aware fallback behavior.
- Multiverse-Core integration through compatibility bridge:
  - Native Bukkit fallback when needed.
  - MV4 and MV5+ compatibility paths.
- Explicit reset phase model:
  - IDLE, PRECHECK, TELEPORT, UNLOAD, DELETE, RECREATE, VERIFY, COMPLETE, FAILED
- Persisted reset state with auto-resume support after interrupted reset.
- Automatic retry attempts on failed full resets (up to 3 retries with backoff).

---

## Requirements
- Spigot API 1.26 (plugin api-version).
- Java 21+ (project is compiled for Java 21).
- Multiverse-Core is strongly recommended and supported as soft dependency.

---

## Installation
1. Stop your server.
2. Back up your worlds and plugin folder.
3. Place the plugin jar in plugins.
4. Start server.
5. Configure resource world and schedule using /rwr gui or config.yml.
6. Verify setup with:
   - /rwr status
   - /rwr next

---

## Upgrade from v3
1. Stop server.
2. Replace old jar with v4 jar.
3. Start server and let migration run.
4. Confirm:
   - config.yml has configVersion: 4
   - config.v3.backup.yml exists
5. Update automation/scripts to /rwr subcommands.
6. Validate with /rwr status and /rwr next.
7. Run one controlled manual reset using /rwr reset now.

### Rollback procedure
1. Stop server.
2. Restore previous v3 jar.
3. Rename current config.yml to config.v4.yml.
4. Restore config.v3.backup.yml as config.yml.
5. Delete reset-state.yml.
6. Start server and validate old behavior.

---

## Quick Start
1. Use /rwr gui.
2. Click Change World and pick your resource world.
3. Set Reset Type (daily/weekly/monthly).
4. Set Restart Time and Warning Time.
5. Optionally enable region mode and add regions.
6. Run /rwr status to confirm schedule and phase state.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| /rwr | Root command (shows help behavior when no subcommand is used) | None at root level |
| /rwr help | Show help page | No admin required |
| /rwr gui | Open admin GUI | resourceworldresetter.admin |
| /rwr reload | Reload config from disk | resourceworldresetter.admin |
| /rwr reset now | Force immediate reset | resourceworldresetter.admin |
| /rwr resume | Resume incomplete reset | resourceworldresetter.admin |
| /rwr resume cancel | Cancel auto-resume and clear incomplete reset state | resourceworldresetter.admin |
| /rwr status | Detailed reset state and schedule info | resourceworldresetter.admin |
| /rwr next | Next reset timestamp, warning time, countdown | resourceworldresetter.admin |
| /rwr region enable | Enable region reset mode | resourceworldresetter.admin |
| /rwr region disable | Disable region reset mode | resourceworldresetter.admin |
| /rwr region list | List configured regions | resourceworldresetter.admin |
| /rwr region add <regionX> <regionZ> | Add region coordinate | resourceworldresetter.admin |
| /rwr region remove <regionX> <regionZ> | Remove region coordinate | resourceworldresetter.admin |
| /rwr region addhere | Add current player region (must be in resource world) | resourceworldresetter.admin |
| /rwr tp | Open world teleport GUI | resourceworldresetter.tp |
| /rwr back | Teleport to recorded previous location | resourceworldresetter.back |

---

## Permissions
- resourceworldresetter.admin
  - Default: op
  - Grants all admin subcommands and GUI admin operations.
- resourceworldresetter.tp
  - Default: true
  - Allows /rwr tp.
- resourceworldresetter.back
  - Default: true
  - Allows /rwr back.

Note: Root /rwr has no single global gate; permissions are enforced per subcommand.

---

## Configuration Reference

### Default structure
```yaml
configVersion: 4
worldName: ""

schedule:
  mode: "daily"
  time:
    hour: 3
  warningMinutes: 5
  day: 1

regions:
  enabled: false
  immediateRegenerationOnAdd: true
  list: []
```

### Top-level keys
- configVersion
  - Schema version. v4 expects 4.
- worldName
  - Resource world name. Empty means not configured yet.
- schedule
  - Reset cadence and timing settings.
- regions
  - Region reset mode and tracked region list.

### schedule keys
- mode
  - Values: daily, weekly, monthly
- time.hour
  - 0 to 23 (24-hour format)
- warningMinutes
  - 0 or more
- day
  - Weekly mode: 1 to 7 (Monday to Sunday)
  - Monthly mode: 1 to 31

### regions keys
- enabled
  - True uses region mode instead of full world reset.
- immediateRegenerationOnAdd
  - If true, adding a region can trigger immediate regeneration of that region when world is available.
- list
  - String list in regionX,regionZ format.

---

## Scheduling Behavior
- Plugin computes next reset based on server timezone.
- Warning task is scheduled independently before reset according to warningMinutes.
- Any relevant config change triggers debounced rescheduling.
- /rwr next shows:
  - next reset timestamp
  - warning timestamp
  - remaining countdown
  - server timezone

---

## Reset Lifecycle and Phases

### Phase flow
IDLE -> PRECHECK -> TELEPORT -> UNLOAD -> DELETE -> RECREATE -> VERIFY -> COMPLETE  
Failure path: any active phase -> FAILED

### Full reset behavior
1. PRECHECK
   - Fires PreResetEvent, can be cancelled by another plugin.
2. TELEPORT
   - Moves players out of target world to safe location in another world.
3. UNLOAD
   - Unloads resource world.
4. DELETE
   - Deletes world folder asynchronously.
5. RECREATE
   - Recreates world.
6. VERIFY
   - Verifies world is loaded and safe spawn is valid.
7. COMPLETE
   - Announces success, clears reset state, reschedules next cycle.

### Failure and retry behavior
- On failure, plugin stores:
  - failed phase
  - reason/detail
  - stack trace (if present)
- Retries up to 3 times with delays:
  - 30s, 60s, 120s
- After max retries, plugin alerts admins and logs critical failure details.

---

## Region Reset Mode

### What region mode does
- Resets only configured regions, not the entire world.
- Teleports players out of affected regions before regeneration.
- Processes chunk unload in batches to reduce main-thread spikes.
- Deletes region data files in:
  - region
  - poi
  - entities

### Region coordinates
- Input format is regionX,regionZ.
- One region = 32x32 chunks.

### Region events
- RegionPreResetEvent
  - Cancellable, skips only that region if cancelled.
- RegionPostResetEvent
  - Reports completion and success flag per region.

---

## Teleport and Back System

### /rwr tp
- Opens world-selection GUI.
- Teleports to safe spawn-like location.
- Tracks previous location for /rwr back.

### /rwr back
- Returns player to last recorded location when valid.
- If resource world was reset since location capture, plugin redirects to:
  1. nearest village safe location when available
  2. safe world spawn fallback otherwise

### Safety behavior
- Teleport and respawn safety checks avoid unsafe landings.
- Plugin tracks safe standing locations per player per world.
- Applies short no-damage window after respawn relocation.

---

## Admin GUI Guide

### Main admin menu
- Current settings overview
- Change World
- Reset Type
- Restart Time
- Warning Time
- Enable/Disable Region Mode
- Manage Regions helper
- Force Reset
- Reload Config

### Specialized menus
- World selection list
- Reset type menu (daily/weekly/monthly)
- Weekly day picker
- Monthly day picker
- Warning minute presets
- Restart hour selector (0-23)

---

## Events for Plugin Developers

### Full reset events
- PreResetEvent
  - Cancellable.
  - Fired before full reset begins.
  - Fields: worldName, resetType.
- PostResetEvent
  - Fired after full reset attempt ends.
  - Fields: worldName, resetType, success.

### Region events
- RegionPreResetEvent
  - Cancellable per region.
  - Fields: worldName, regionX, regionZ.
- RegionPostResetEvent
  - Fired after each region attempt.
  - Fields: worldName, regionX, regionZ, success.

---

## Operations Runbook

### Daily checks
- Run /rwr status.
- Run /rwr next.
- Review logs for:
  - migration output
  - failed phase details
  - resume actions

### If reset is interrupted
1. Run /rwr status.
2. Decide:
   - /rwr resume to continue
   - /rwr resume cancel to clear pending resume state
3. Address root issue before retrying repeatedly (disk, world ops, dependency issues).

### After upgrades
- Verify backup config exists.
- Validate schedule and world selection.
- Run one supervised manual reset during low population.

---

## Troubleshooting

### Old command not found
Cause: Legacy commands removed in v4.  
Fix: Use /rwr subcommands only.

### No reset occurs
Check:
- worldName is configured
- schedule.mode and schedule.time.hour are valid
- plugin has access to world container for delete/recreate
- dependency environment is healthy

### Auto-resume message appears on startup
Cause: previous reset was interrupted.  
Fix:
- /rwr resume to continue
- /rwr resume cancel to clear and abort recovery path

### Region command says invalid coordinates
Cause: Non-integer regionX/regionZ input.  
Fix: Use integer values, for example:
```text
/rwr region add 0 0
/rwr region remove -1 2
```

### Back teleport sends player to spawn or village
Cause: Their stored previous location was in a resource world that has since been reset.  
Fix: This is expected safety behavior.

### Metrics behavior
- Metrics are enabled unless disabled by config key metrics.enabled.
- If you do not want telemetry, set metrics.enabled to false and reload.

---

## FAQ

### Does plugin need Multiverse-Core?
It is configured as a soft dependency and uses compatibility bridging. It can operate with Bukkit world APIs, while integrating with Multiverse when available.

### Can non-admin players use teleport tools?
Yes. /rwr tp and /rwr back are permission-gated separately and default to true.

### Can another plugin block a reset?
Yes. PreResetEvent is cancellable and can stop full reset start.

### Is region reset safer for active servers?
Region reset can reduce full-world disruption and supports chunk-batch processing, but you should still test performance and behavior on your server setup.
