## Plan: ResourceWorldResetter reliability improvements

Rework scheduling, config validation, and reset execution so resets happen on-time, avoid duplicate scheduling, and reduce main-thread blocking.

**Steps**
1. ~~**Config validation layer**: In ResourceWorldResetter.java add a `validateAndApplyConfig()` pass that clamps `restartTime` to 0-23, ensures `resetDay` falls within valid ranges for weekly/monthly modes, and normalizes `resetType` to a known enum. Log and fallback to defaults when invalid values are found. Update `loadConfig()` and setters to route through this validator before mutating state.~~ ✅ **DONE**
2. ~~**Centralized rescheduling**: Introduce a `RescheduleManager` helper (inner class or dedicated methods) that tracks warning/reset task IDs, cancels existing tasks, and schedules new ones after configuration changes. Expose a single `requestReschedule()` method that debounces multiple setter calls by using a short delayed runnable (e.g., 1 tick) to avoid immediate repeated scheduling.~~ ✅ **DONE**
3. ~~**Separate warning scheduling**: Split `scheduleDailyReset()` into `computeNextResetInstant()` and `scheduleWarningTask()` so warning broadcasts are scheduled independently and can be skipped/reapplied without rebuilding the main reset task.~~ ✅ **DONE**
4. ~~**Async reset safety**: Wrap the `CompletableFuture.runAsync()` block in `performReset()` with `whenComplete()` logging, propagate failures back to the main thread, and ensure world recreation only triggers after deletion succeeds. Add timeout/logging if deletion exceeds a threshold and guard against `resetTaskId == -1` edge cases.~~ ✅ **DONE**
5. ~~**Region reset throttling**: Replace the synchronous double-loop in `regenerateRegion()` with chunk unload/regeneration batches executed via Bukkit scheduler (e.g., process N chunks per tick) to avoid large blocking operations. Ensure POI/entity data cleared asynchronously before deleting .mca files.~~ ✅ **DONE**
6. ~~**Player/world safety checks**: Harden `teleportPlayersSafely()` and `teleportPlayersOutOfRegions()` to handle missing worlds gracefully, log when teleport target differs from default, and ensure `regionsToReset` parsing reports invalid entries back to config rather than silently swallowing.~~ ✅ **DONE**
7. ~~**Logging & metrics**: Expand LogUtil usage to include warning/reschedule decisions, include precise timestamps/timezone, and feed new schedule state into bStats custom charts (`next_reset_epoch`).~~ ✅ **DONE**

**Relevant files**
- `src/main/java/com/lozaine/ResourceWorldResetter/ResourceWorldResetter.java` — add validator, scheduling helpers, async error handling, region throttling, and improved logging.
- `src/main/java/com/lozaine/ResourceWorldResetter/utils/LogUtil.java` — ensure it supports timestamp formatting for new logs (only if needed).

**Verification**
1. Run `./gradlew build` to ensure compilation succeeds.
2. Start a local Spigot/Paper test server with the plugin, toggle config values (daily/weekly/monthly) and confirm next reset + warnings log correctly.
3. Trigger `/resetworld` and `/rwrregion` on a test world populated with players to confirm teleports, folder deletion, and region regeneration complete without TPS drops.
4. Toggle metrics enabled/disabled to ensure new chart data appears without NPEs.

**Decisions**
- Assume server timezone equals desired schedule; future enhancement could add explicit timezone/duration fields but out-of-scope for this pass.
- Region throttling prioritizes server stability over raw speed; accepting slightly longer resets in exchange for keeping TPS stable.

---

## Plan: Future Enhancements & New Features

**PHASE 1 (ESSENTIAL – Reliability & Integration)**

1. ~~**Pre/Post-Reset Bukkit Events** — Create custom events (`PreResetEvent`, `PostResetEvent`, `RegionPreResetEvent`, `RegionPostResetEvent`) that other plugins can listen to for coordinated cleanup or validation. Events should include world name, reset type, and allow cancellation for pre-events. Enables ecosystem integration without hardcoding.~~ ✅ **DONE**

2. ~~**Reset Failure Recovery** — Add automatic retry logic with exponential backoff (3 attempts, 30s/60s/120s delays) on reset failure. Broadcast admin alerts on repeated failures. Log failure reason and suggest next recovery step. Prevents player disruption from transient errors.~~ ✅ **DONE**

3. ~~**Graceful Shutdown Handler** — Save reset state to file before server stops if reset is mid-execution. On plugin enable, detect incomplete resets and offer admin choice to resume or rollback (if backup exists). Critical for production reliability.~~ ✅ **DONE**

**PHASE 2 (HIGH PRIORITY – Robustness & Safety)**

4. **World Backup Before Reset** — Implement automatic backup creation before world deletion. Store backups in `plugins/ResourceWorldResetter/backups/` with timestamp naming. Provide admin command to restore from backup. Useful for recovery if reset encounters unexpected issues.

5. **Configuration Reload Safety** — Enhanced validation for `/reloadrwr` to prevent mid-reset config changes. If reset is in progress, queue the reload to fire after completion. Warn admin if critical values changed. Prevents race conditions and config corruption.

6. **Multiple World Support** — Extend plugin to manage N independent resource worlds, each with own schedule, reset mode, and region list. Modify config to support world profiles (e.g., `worlds.resourceA`, `worlds.resourceB`). Update GUI to switch between worlds. Unlocks multi-resource-area servers.

**PHASE 3 (MEDIUM – Observability & Control)**

7. **Performance Tracking Dashboard** — Track reset duration, TPS before/after, and region reset times in memory. Expose via command `/rwrstats` showing last 10 reset attempts with duration/TPS metrics. Feed trend data to bStats if enabled. Helps admins diagnose performance issues.

8. **Player Activity Threshold** — Add config option `resetOnlyIfPlayerActivityBelow` (0-100, % of max players). Before scheduled reset, check online player count and skip if exceeded. Log activity check decision. Minimizes player disruption on high-traffic servers.

9. **WorldGuard Integration** — Add optional WorldGuard region support for region-based resets. Allow admins to specify WorldGuard region names (e.g., "mining_area") instead of manual region coordinates. Use WorldGuard API to fetch region boundaries, convert to Minecraft region coordinates, and reset all overlapping regions. Support both modes: WorldGuard regions OR manual coordinates. Add soft-depend in plugin.yml and graceful fallback if WorldGuard unavailable. Update GUI and commands to work with region names.

**PHASE 4 (POLISH – Advanced Features)**

10. **Webhook Integration** — Optional Discord/webhook notification for reset start/completion with TPS impact summary. Configurable per webhook URL in config. Keeps remote admins informed.

11. **Advanced Scheduling (Cron-like)** — Allow cron expressions or advanced scheduling UI for complex schedules (e.g., reset every 2 weeks on Tuesday, or daily except weekends). Adds scheduling flexibility for large networks.

12. **Reset Command Queuing** — Allow stacking multiple `/rwrregion` commands to batch reset multiple regions in sequence with pause between each. Enables bulk region management via commands.

13. **Config Import/Export** — Backup and restore plugin config as JSON for easy migration between servers. Simplifies server replication.

**Recommended Roadmap Order**
- **Phase 1 (v3.1.0)**: Pre/Post-Reset Events → Reset Failure Recovery → Graceful Shutdown Handler
- **Phase 2 (v3.2.0)**: World Backup → Config Reload Safety → Multiple World Support
- **Phase 3 (v3.3.0)**: Performance Tracking → Player Activity Threshold → WorldGuard Integration
- **Phase 4 (v4.0.0)**: Webhook Integration, Advanced Scheduling, Command Queuing, Config Import/Export
