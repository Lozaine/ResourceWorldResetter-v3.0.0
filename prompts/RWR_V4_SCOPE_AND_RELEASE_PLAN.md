# ResourceWorldResetter v4+ Roadmap

## Vision & Goals
- **Breaking change with confidence**: Ship v4.0.0 with clear migration path and auto-tooling.
- **Safety-first evolution**: Move from implicit to explicit state; prevent admin surprises.
- **Operator visibility**: Expose reset internals in-game; reduce support burden.
- **Low upgrade friction**: Automatic migration, clear docs, fast rollback option.

## Versioning Strategy
- **v4.0.0**: Breaking change release (new config schema, new command tree, basic state machine).
- **v4.1.0**: Production hardening (deterministic resume, preflight gates, complete tests, full ops docs).
- **v4.2.0**: DX enhancements (enhanced events, validation tools, metrics, QoL).
- **v5.0.0** (future): Remove v3 compat layer, major architectural changes, and multi-world reset support (db persistence, webhooks, orchestration).

## Release Priority
**Recommended release sequence**: v4.0.0 → v4.1.0 → v4.2.0
- v4.0.0 is the breaking change MPV (safe for early adopters)
- v4.1.0 makes it production-ready (safety gates + deterministic recovery)
- v4.2.0 adds operational polish (validation, richer events, metrics)

---

## v4.0.0: Breaking Change MVP (Early Adopter Release)

### What v4.0.0 includes
The goal is a clean, focused breaking change that makes upgrading predictable. Focus on what's ready; defer complex features to v4.1.0.

#### 1) Versioned config schema + automatic migration ✓
- `configVersion: 4` field in `config.yml`.
- Structured schedule block (`schedule.mode`, `schedule.time`, `schedule.warningMinutes`, `schedule.day`).
- Automatic one-time migration from v3 schema on startup.
- Backup file: `config.v3.backup.yml`.
- Migration validation logging.
- **Status**: ✓ DONE

#### 2) New command tree (`/rwr` subcommands) ✓
- `/rwr gui` – open admin GUI
- `/rwr reload` – reload configuration
- `/rwr reset now` – trigger manual reset
- `/rwr resume [cancel]` – resume/cancel in-progress reset
- `/rwr region <list|enable|disable|add|remove|addhere>` – region management
- `/rwr status` – show current reset state (basic info)
- `/rwr next` – show next scheduled reset time
- Old commands (`/rwrgui`, `/reloadrwr`, etc.) are **not registered** (intentional breaking change).
- **Status**: ✓ DONE

#### 3) Basic state machine with persistence
- `ResetPhase` enum: `IDLE`, `PRECHECK`, `TELEPORT`, `UNLOAD`, `DELETE`, `RECREATE`, `VERIFY`, `COMPLETE`, `FAILED`.
- Persist current phase in reset state file for visibility.
- `/rwr status` shows current phase (e.g., `IN_PROGRESS: TELEPORT`).
- Track phase transitions and basic metadata (timestamp, attempt count).
- **Status**: ✓ DONE (basic structure; deterministic resume deferred to v4.1.0)

#### 4) Essential documentation for v4.0.0
- **README.md**: Breaking changes prominently documented, new command quick reference, basic troubleshooting.
- **MIGRATION.md**: Step-by-step upgrade from v3 to v4, permission node changes, rollback procedure.
- **plugin.yml**: Updated with new command structure and usage help.
- **Spigot description**: Highlight breaking changes and new command model.
- **In-game help**: `/rwr help` command with descriptions for each subcommand.
- **NOT included in v4.0.0**: OPERATIONS.md (defer to v4.1.0 after preflight gates ship).

### What v4.0.0 does NOT include (deferred to v4.1.0)
- **Preflight gates** (TPS/players/disk checks) – too many moving parts, defer for safety validation
- **Deterministic resume on restart** – phase transition hooks and safe resume logic; risky without full testing
- **Gate broadcasting and detailed `/rwr status` reporting** – depends on preflight gates
- **Full test suite** – unit & integration tests for all features

### v4.0.0 Release Checklist

#### A) Engineering readiness
- [x] Implement config schema v4 and migration code.
- [x] Add migration backup and idempotency tests.
- [x] Implement `/rwr` command tree (no legacy aliases).
- [x] Add `ResetPhase` enum and basic state persistence.
- [x] Implement phase transition hooks and deterministic resume logic. **DEFER TO V4.1.0**
- [x] Implement `/rwr status` with phase-level detail (v4.0.0: basic, v4.1.0: enhanced).
- [x] Implement `/rwr next` command with next reset time display.
- [x] Update plugin.yml metadata and command descriptions.
- [x] Create comprehensive MIGRATION.md with step-by-step upgrade path.
- [x] Update README.md with breaking changes, new command reference, and basic troubleshooting.

#### B) Core test coverage (v4.0.0 minimum)
- [x] Unit tests: config migration mappings, fallback defaults.
- [x] Unit tests: config validation and v3 → v4 schema transformation.
- [x] Unit tests: ResetPhase enum and phase definitions.
- [x] Unit tests: command routing and permission checks for new `/rwr` tree.
- [x] Manual smoke tests: startup on clean server, startup with v3 config.
- [x] Manual smoke tests: execute each `/rwr` subcommand and verify response.
- [x] Regression tests: region reset flow still works with new state machine.

#### C) Upgrade & compatibility verification
- [x] Test startup on clean server (no prior config): auto-generates v4 config.
- [x] Test startup on v3 config: one-time migration, backup created, no errors in logs.
- [x] Verify old commands return command-not-found error (no aliases; intentional breaking change).
- [x] Verify jar naming and plugin.yml version alignment (e.g., RWR-4.0.0.jar).
- [x] Verify config examples in README and MIGRATION are syntactically valid.
- [x] Verify migration logs clearly indicate success or failure.

#### D) Documentation completeness
- [x] README.md: "Breaking Changes" section at top; old → new command mapping; config examples.
- [x] README.md: Troubleshooting section covering migration failures, config validation errors.
- [x] MIGRATION.md: Pre-upgrade checklist, step-by-step upgrade procedure, post-upgrade verification.
- [x] MIGRATION.md: Rollback procedure (restore v3 jar, restore config backup, reset state handling).
- [x] Spigot page: Updated description emphasizing v4 as breaking change with migration support.
- [x] In-game help: `/rwr help` output complete and helpful for all subcommands.
- [ ] Known issues list (if any) documented with workarounds.

#### E) Release execution
- [ ] Tag `v4.0.0-rc1` and test upgrade on real v3 server (if available).
- [ ] Collect RC feedback from 1–2 early adopters on upgrade experience.
- [ ] Fix any RC blockers (migration failures, command registration errors).
- [ ] Tag final `v4.0.0`.
- [ ] Publish release notes highlighting **breaking changes at top**, migration path, and support contact.
- [ ] Publish to Spigot with updated description.
- [ ] Announce on Discord/forums with migration quick-start and support offering.

### Exit criteria for v4.0.0
- ✓ Config schema and command tree are breaking changes; old commands do not work.
- ✓ Config migration is automated; backup is created; startup is clean.
- ✓ ResetPhase enum exists and phases are logged; `/rwr status` shows current phase.
- ✓ README and MIGRATION.md are complete and tested; examples work.
- ✓ At least one RC cycle with external feedback.
- ✓ Minimum smoke tests pass; no obvious regressions from v3 reset flow.
- ✓ Documentation is clear enough for admins to self-upgrade without dev support.

---

## v4.1.0: Production Hardening (Full Feature Release)

### What v4.1.0 adds
Building on v4.0.0, this release hardens the plugin for production use by adding safety gates, deterministic recovery, and comprehensive testing.

#### 1) Deterministic resume on server restart
- On plugin startup, detect incomplete reset from state file.
- Resume from last safe phase instead of restarting from IDLE.
- Implement phase transition hooks (`onEnter`, `onExit`) for safety checks before moving to next phase.
- If a phase fails, offer manual recovery options:
  - Automatic retry (up to 3 attempts)
  - Manual phase skip (admin approval)
  - Full abort (reset marked FAILED, scheduled retry at next cycle)
- Log resume attempts and outcomes clearly.

#### 2) Preflight gates (TPS, players, disk)
- **TPS gate**: `preflight.tps.min` (default: 15.0) – abort if TPS < threshold.
- **Player count gate**: `preflight.players.max` (default: 0 = disabled) – abort if online players > threshold.
- **Disk free gate**: `preflight.disk.freeGB` (default: 5.0) – abort if free disk < threshold.
- Configurable failure policy per gate:
  - `delay` – retry gate check after N minutes (up to 3 retries)
  - `abort` – skip this reset cycle, retry at next scheduled time
  - `force` – reset anyway (not recommended; emergency only)
- Broadcast admin notice when gate blocks reset: `[RWR] Reset blocked by {gate}: {reason}. Retry in N min.`
- Log all gate blocks with metrics (count, total delay, block reason).

#### 3) Enhanced `/rwr status` output
- Current reset state and exact phase (e.g., `INCOMPLETE_RESET IN_PROGRESS: TELEPORT`)
- Current mode/schedule (daily, weekly, manual, etc.)
- Next reset time (ISO 8601 + human-readable) and warning time
- Retry attempt count and max retries
- Last failure reason (phase where it failed, exception message)
- Whether incomplete reset auto-resume is queued and timestamp
- **Preflight gate status**: Current or last block reason, how long until retry
- Estimated vs actual reset duration (from state file)
- Colored, easy-to-parse format; suitable for logging
- For region resets: region name and region-specific status

#### 4) Comprehensive testing
- **Unit tests**:
  - Preflight gate logic (TPS/players/disk checks, threshold evaluation).
  - Gate failure policy evaluation (delay, abort, force).
  - ResetPhase transitions and edge cases (e.g., abort during TELEPORT, resume from FAILED).
  - Phase hook execution and side effects.
  - State file serialization/deserialization.
- **Integration tests**:
  - Incomplete reset recovery across server restart.
  - Gate-block behavior: broadcast, retry, eventual completion.
  - State file persistence and recovery scenarios.
  - Multi-phase resets with phase-level timing.
  - Retry backoff and max-retry limit enforcement.
- **Regression tests**:
  - Region reset flow still works end-to-end.
  - Warning scheduling and broadcasts.
  - Player teleportation during TELEPORT phase.
  - World deletion and recreation.

#### 5) Complete operational documentation
- **OPERATIONS.md** (new):
  - State machine lifecycle diagram (ASCII or Mermaid).
  - Explanation of each phase: purpose, typical duration, common failure modes.
  - How to read `/rwr status` output: what each field means, how to interpret.
  - Preflight gate tuning guide: how to set TPS/players/disk thresholds for your server.
  - Reset failure recovery: phase-by-phase troubleshooting (e.g., "stuck in UNLOAD? Check world lock file").
  - Manual recovery options: phase skip, full abort, retry.
  - Log file interpretation: what to look for in logs, common warning/error patterns.
  - Performance tuning: phase timeout tweaks, batch operation sizes, etc.
- **README.md updates**:
  - Add "Safety Features" section explaining preflight gates.
  - Add link to OPERATIONS.md for advanced users.
  - Supported server matrix: Spigot versions, Java versions, world size limits.
  - Performance expectations: typical reset duration by world size.

#### 6) Configuration enhancements
- Add full `preflight.*` config block with TPS, players, disk gates and failure policies.
- Add optional phase timeout overrides in config.
- Validate config on startup; warn about missing/invalid preflight settings.
- Provide sensible defaults so config works out-of-the-box.

### What v4.1.0 does NOT include (deferred to v4.2.0+)
- Enhanced event API with rich payloads
- Dry-run mode (`/rwr validate`)
- Webhooks/Discord notifications
- Database persistence

### v4.1.0 Release Checklist

#### A) Engineering: Phase transition hooks & deterministic resume
- [ ] Implement phase transition hook system (`onEnter`, `onExit` callbacks).
- [ ] Implement deterministic resume logic on plugin startup.
- [ ] On startup, load state file and resume from last non-IDLE phase if present.
- [ ] Add phase failure handling: automatic retry, manual skip, full abort options.
- [ ] Add manual override commands for testing (e.g., `setPhase`, `forceResume`).
- [ ] Ensure state file is written after every phase transition.
- [ ] Add "stuck detection": if phase duration exceeds configurable timeout, emit warning and offer manual options.

#### B) Engineering: Preflight gates
- [ ] Implement TPS gate check logic (poll TPS, compare to threshold).
- [ ] Implement player count gate check logic.
- [ ] Implement disk free space gate check logic.
- [ ] Implement gate failure policy engine (delay, abort, force).
- [ ] Add gate configuration parsing and validation.
- [ ] Implement gate broadcast messaging when reset is blocked.
- [ ] Add gate status tracking and reporting in state file.
- [ ] Ensure gates run for **all** scheduled resets (not manual resets; configurable).

#### C) Engineering: Status & next commands
- [ ] Enhance `/rwr status` to show all fields listed above (phase, gate status, timing, etc.).
- [ ] Ensure `/rwr next` shows correct next reset time with warning time.
- [ ] Add colored output for readability in-game.
- [ ] Add fallback plain-text output for console logs.

#### D) Configuration & defaults
- [ ] Add `preflight` config block with TPS, players, disk sub-sections.
- [ ] Set sensible defaults for all preflight gates.
- [ ] Add config validation: warn if gates are misconfigured.
- [ ] Document all config keys in inline comments and README.

#### E) Comprehensive testing
- [ ] Unit tests: preflight gate logic (all 3 gates, all policies).
- [ ] Unit tests: ResetPhase transitions and edge cases (abort, stuck, retry).
- [ ] Unit tests: phase hook execution and side effects.
- [ ] Unit tests: state file I/O and recovery.
- [ ] Integration tests: incomplete reset recovery (setup state, restart, verify resume).
- [ ] Integration tests: gate blocks (set up conditions, verify block, verify retry).
- [ ] Integration tests: multi-phase reset end-to-end.
- [ ] Integration tests: timing and broadcast verification.
- [ ] Regression tests: v4.0.0 features still work (config migration, commands, basic state).
- [ ] Stress tests: rapid restart cycles, concurrent reset+gate scenarios.
- [ ] Test coverage target: >80% line coverage for core logic, >90% for state machine.

#### F) Documentation completeness
- [ ] Create OPERATIONS.md with all sections listed above.
- [ ] Update README.md: add safety features section, preflight gate overview, tuning guide.
- [ ] Add performance expectations and typical reset duration breakdown.
- [ ] Add troubleshooting section for gate blocks, phase timeouts, stuck resets.
- [ ] Add example configs showing different preflight policies.
- [ ] Create state machine lifecycle diagram (ASCII or Mermaid).
- [ ] Update Spigot page description to mention safety gates and deterministic recovery.

#### G) Upgrade verification from v4.0.0
- [ ] Test v4.0.0 server upgrade to v4.1.0 (config migration, state handling).
- [ ] Verify incomplete v4.0.0 resets resume correctly under v4.1.0.
- [ ] Verify preflight gates don't incorrectly block on first startup.
- [ ] Verify new config keys have correct defaults and don't cause errors on v4.0.0 configs.

#### H) Release execution
- [ ] Tag `v4.1.0-rc1` and test on real/simulated server.
- [ ] Collect feedback from at least 2 adopters on gate behavior and recovery.
- [ ] Fix any RC blockers (gate false positives, state file corruption, resume failures).
- [ ] Tag final `v4.1.0`.
- [ ] Publish release notes: "Production hardening release" headline, emphasis on safety gates and deterministic recovery.
- [ ] Publish to Spigot; update Spigot page with safety features and OPERATIONS guide link.
- [ ] Announce on Discord/forums: "v4.1.0 is production-ready" message with gate tuning examples.

### Exit criteria for v4.1.0
- ✓ Incomplete resets deterministically resume from last safe phase on restart.
- ✓ Preflight gates (TPS, players, disk) block resets when conditions are not met.
- ✓ Gate failures broadcast admin notices; status is visible in `/rwr status`.
- ✓ Phase timeout detection and manual recovery options work.
- ✓ OPERATIONS.md is complete with troubleshooting and tuning guides.
- ✓ Test coverage >80% for core logic; integration tests verify recovery and gate behavior.
- ✓ At least one RC cycle with external feedback on safety gates and recovery.
- ✓ Plugin is safe for production use on live servers with active players.
- ✓ Admin support load reduced: status visibility + deterministic recovery + clear troubleshooting.

---

## v4.2.0: DX Enhancements & Extensibility (Polish Release)

## v4.2.0: DX Enhancements & Extensibility (Polish Release)

### What v4.2.0 adds
Building on the solid v4.1.0 foundation, this release adds developer-facing features and operational convenience.

#### 1) Enhanced event API
- Extend existing event classes with richer payloads:
  - `PreResetEvent`: include phase info, preflight gate status, estimated duration.
  - `PostResetEvent`: include actual duration, phase breakdown, gate blocks that occurred.
  - `RegionPreResetEvent` / `RegionPostResetEvent`: similar enhancements.
- New `ResetMetricsEvent` for plugins to hook into detailed metrics.
- Add `ResetPhaseTransitionEvent` for phase-by-phase tracking by third-party plugins.
- All new fields are additive; no breaking changes to existing listeners.

#### 2) Dry-run validation (`/rwr validate`)
- `/rwr validate` command: check config validity without triggering reset.
- Verify world access and file permissions.
- Check disk space, TPS thresholds, player counts.
- Simulate preflight gates and report would-be-blocked status.
- Useful for pre-flight checks before maintenance window.

#### 3) Region management improvements
- `/rwr region export` – export region list to JSON for backup/sharing.
- `/rwr region import <file>` – bulk import region configs.
- `/rwr region rename <old> <new>` – rename region.
- Named region sets for easier management on large servers.

#### 4) Expanded metrics
- Track success/failure rates over time (last 7 days, 30 days, all-time).
- `/rwr metrics` command showing:
  - Total resets, successful resets, failed resets
  - Mean, median, min, max reset duration
  - Preflight gate block frequency and reasons
  - Phase-level timing breakdown
- Metrics persist in separate file for long-term tracking.
- Integration with Spigot metrics system (opt-in).

#### 5) Minor QoL improvements
- Add `/rwr config show` to display current config in-game (redacted for sensitive data).
- Add `/rwr logs recent` to show recent RWR log entries in-game.
- Add `/rwr test <phase>` for testing individual phase behavior (admin-only, not on production).
- Improved error messages: point directly to troubleshooting section in OPERATIONS.md.

### What v4.2.0 does NOT include (deferred to v5.0.0+)
- Multi-world profiles and batch resets, including Nether/End dimension selection.
- Discord/webhook notifications (requires external integration layer)
- Database persistence (architectural change; belongs in v5)
- GUI redesign (low priority; current GUI works fine)
- Cross-server orchestration

### v4.2.0 Release Checklist
#### A) Engineering: Enhanced event API
- [ ] Add phase info to `PreResetEvent` and `PostResetEvent`.
- [ ] Add gate status and gate blocks to event payloads.
- [ ] Add actual duration and phase timing to `PostResetEvent`.
- [ ] Create `ResetPhaseTransitionEvent` for phase transitions.
- [ ] Create `ResetMetricsEvent` for metrics reporting.
- [ ] Ensure all new fields are Optional/nullable for backwards compatibility.
- [ ] Add comprehensive Javadocs for all new event fields.

#### B) Engineering: Validation & dry-run
- [ ] Implement `/rwr validate` command (check config, permissions, disk space, TPS).
- [ ] Simulate preflight gates without actually blocking reset.
- [ ] Add detailed validation report showing would-be failures.
- [ ] Add `--test` flag to `/rwr reset now` for dry-run on manual resets.

#### C) Engineering: Region management QoL
- [ ] Implement `/rwr region export <file>`.
- [ ] Implement `/rwr region import <file>`.
- [ ] Implement `/rwr region rename <old> <new>`.
- [ ] Add validation for imported regions.

#### D) Engineering: Metrics
- [ ] Implement metrics persistence (separate YAML file).
- [ ] Track success/failure rates with timestamps.
- [ ] Track phase-level timing.
- [ ] Implement `/rwr metrics` command with summary display.
- [ ] Integrate with Spigot metrics system (optional, plugin-opt-in).

#### E) Engineering: Minor QoL
- [ ] Implement `/rwr config show` (redact passwords, API keys).
- [ ] Implement `/rwr logs recent <count>` showing recent RWR log lines.
- [ ] Implement `/rwr test <phase>` for phase testing (admin-only).
- [ ] Improve error messages to include doc links.

#### F) Testing
- [ ] Integration tests: event payload verification for all event types.
- [ ] Integration tests: metrics tracking and persistence.
- [ ] Integration tests: validation command on various config edge cases.
- [ ] Regression tests: all v4.0.0 and v4.1.0 features still work with new code.

#### G) Documentation
- [ ] Update OPERATIONS.md: add metrics interpretation section.
- [ ] Document new event payloads for plugin developers.
- [ ] Add troubleshooting for common profile config mistakes.
- [ ] Document region import/export workflow.

#### H) Release execution
- [ ] Tag `v4.2.0-rc1` and test validation/event/metrics behavior.
- [ ] Test event compatibility with existing third-party plugins.
- [ ] Verify metrics tracking over 7+ day period.
- [ ] Fix any RC blockers.
- [ ] Tag final `v4.2.0`.
- [ ] Publish release notes: "DX & operational enhancements" headline.
- [ ] Announce: "Now with enhanced events, validation, and metrics!"

### Exit criteria for v4.2.0
- ✓ Event payloads are rich and backwards-compatible.
- ✓ Validation command is useful for pre-reset verification.
- ✓ Metrics are tracked and accessible via `/rwr metrics`.
- ✓ Region import/export works for bulk management.
- ✓ All v4.1.0 features still work; no regressions.
- ✓ Test coverage remains >80% for core logic.

---

## v5.0.0 (Future): Architectural Refresh

### Planned for v5.0.0 (NOT v4.x; reserved for major changes)
- Remove v3 compatibility layer entirely (v4.x was last version supporting v3-compat).
- Add multi-world reset support, including explicit world sets, Nether/End inclusion controls, and per-world resource limits.
- Implement database persistence layer option (PostgreSQL, MySQL, SQLite).
- Add webhook/Discord notification system for reset events.
- Add cross-server orchestration API.
- Major GUI redesign.
- These are **not** needed for v4.x; kept for v5.0.0 to avoid scope creep.

---

## Recommended Release Timeline & Rationale

### Phase 1: v4.0.0 (Early Adopter Release)
- **Timeline**: When config migration + new command tree are stable and tested.
- **Rationale**: Introduce breaking change cleanly; lock down schema and command model.
- **Target audience**: Early adopters who want new commands and are okay with migration friction.
- **Risk level**: Low (mostly moving pieces around; no new complex logic).

### Phase 2: v4.1.0 (Production Hardening)
- **Timeline**: 2–4 weeks after v4.0.0 (allow RC feedback integration).
- **Rationale**: Add safety gates and deterministic recovery; production servers need these.
- **Target audience**: Production server owners who need safety guarantees.
- **Risk level**: Medium (state machine and resume logic are critical; needs solid testing).

### Phase 3: v4.2.0 (Polish & DX)
- **Timeline**: 4–6 weeks after v4.1.0 (optional; nice-to-have).
- **Rationale**: Add quality-of-life features; plugin authors benefit from enhanced events.
- **Target audience**: Plugin developers and power users.
- **Risk level**: Low (mostly additive features; non-critical).



---

## Breaking Changes (v4.0.0 only)

### What breaks in v4.0.0 (intentional)
1. **Old commands are gone**: `/rwrgui`, `/reloadrwr`, `/resetworld`, `/rwrregion`, `/rwrresume` are **not registered** in v4.x. Server owners must update all scripts and automation to use `/rwr` subcommands.
2. **Config schema changed**: Flat scheduling keys moved under `schedule.*` block. Auto-migration happens once on startup; config is rewritten.
3. **Reset-state file format changed**: v4 uses explicit phases; v3 format is incompatible. Old reset-state.yml is archived on first startup.
4. **Reset behavior is stateful**: v4 resets do not restart from scratch on server restart; they resume from last safe phase (in v4.1.0+).

### Backward compatibility strategy
- **Config**: Automatic one-time migration from v3 to v4 on first startup. Old keys are read, mapped to new schema, config rewritten, backup saved as `config.v3.backup.yml`.
- **Commands**: Intentional breaking change; no aliases. Forces admins to explicitly update scripts.
- **Reset-state**: v3 format archived with timestamp. v4 state machine starts fresh.
- **API/Events**: Existing event classes remain compatible. New fields are additive (v4.1.0+).

### Upgrade from v3 to v4.0.0
1. Back up `plugins/ResourceWorldResetter/` and world files.
2. Stop server.
3. Replace jar with v4.0.0.
4. Start server; review migration logs.
5. Update all scripts and automation to use `/rwr` subcommands.
6. Verify commands: `/rwr status`, `/rwr next`, `/rwr gui`.
7. Keep `config.v3.backup.yml` until first successful reset.

### Rollback from v4 to v3
1. Stop server.
2. Restore v3 jar from backup.
3. Restore config: move `config.yml` → `config.v4.yml`, move `config.v3.backup.yml` → `config.yml`.
4. Delete v4 `reset-state.yml` (do not restore v3 format).
5. Start server; old commands are available; verify scheduling works.

---

## Upgrade Paths & Migration Strategy

### v3 → v4.0.0 (Breaking change introduction)
**Who should upgrade**: Early adopters, testing servers, admins comfortable with scripting changes.
**What's automatic**: Config schema migration, command routing to new `/rwr` tree.
**What admins must do**: Update all scripts/automation, verify preflight gate defaults, test a manual reset.
**Risk**: Low if done carefully; rollback is straightforward.
**ROI**: New command structure, better logging, foundation for safety features in v4.1.0.

### v4.0.0 → v4.1.0 (Production hardening)
**Who should upgrade**: All production servers; safety gates are critical.
**What's automatic**: Preflight gates use sensible defaults; resume logic activates.
**What admins should do**: Review preflight gate settings (TPS, players, disk), run `/rwr status` to verify, test gate behavior during low-activity window.
**Risk**: Low; gates are conservative by default, resume is designed for safety.
**ROI**: Deterministic recovery from crashes, prevented resets during high load, admin visibility via `/rwr status`.

### v4.1.0 → v4.2.0 (Polish & optional features)
**Who should upgrade**: Servers using third-party plugins, metrics-oriented admins.
**What's automatic**: Nothing required.
**What admins can enable**: Hook into enhanced events, enable metrics.
**Risk**: Very low; all v4.2.0 features are opt-in.
**ROI**: Richer data for analysis, better plugin integration, and simpler release scope.

---

## Decision Matrix: When to Release Which Version

| Criteria | v4.0.0 Ready? | v4.1.0 Ready? | v4.2.0 Ready? |
|----------|:--------:|:--------:|:--------:|
| **Config migration & commands** | ✓ YES | ✓ YES | ✓ YES |
| **Basic state machine logging** | ✓ YES | ✓ YES | ✓ YES |
| **Deterministic resume on restart** | ✗ NO | ✓ YES | ✓ YES |
| **Preflight gates (TPS/players/disk)** | ✗ NO | ✓ YES | ✓ YES |
| **Complete test suite** | ~ PARTIAL | ✓ YES | ✓ YES |
| **MIGRATION.md documentation** | ✓ YES | ✓ YES | ✓ YES |
| **OPERATIONS.md documentation** | ✗ NO | ✓ YES | ✓ YES |
| **Multi-world profiles** | ✗ NO | ✗ NO | ✗ NO |
| **Enhanced event API** | ✗ NO | ✗ NO | ✓ YES |
| **Production-safe for live servers** | ~ MAYBE | ✓ YES | ✓ YES |

**Interpretation**:
- **v4.0.0 is ready ASAP** if config migration + commands are stable and README/MIGRATION docs are done. It's safe for early adopters and test servers.
- **v4.1.0 is mandatory before production** if you want safety gates and deterministic recovery. Most admins should wait for v4.1.0.
- **v4.2.0 is optional polish** for specific use cases (custom plugins, metrics).

---

## Development Priority & Implementation Order

### Fastest path to v4.0.0 (breaking change MVP)
1. ✓ Config migration + migration tests.
2. ✓ `/rwr` command tree with basic dispatch.
3. ✓ `ResetPhase` enum and state persistence.
4. → `[ ] `/rwr status` and `/rwr next` commands (show phase and next reset).
5. → `[ ]` README.md: breaking changes, new commands, quick reference.
6. → `[ ]` MIGRATION.md: step-by-step upgrade, rollback procedure.
7. → `[ ] ` Manual smoke tests and RC cycle.
8. → `[ ] ` Release v4.0.0.

### Fastest path to v4.1.0 (production hardening)
1. (v4.0.0 shipped)
2. → `[ ] ` Phase transition hooks (`onEnter`, `onExit`).
3. → `[ ] ` Deterministic resume logic on startup.
4. → `[ ] ` Preflight gate logic (TPS, players, disk checks).
5. → `[ ] ` Gate failure policy engine (delay, abort, force).
6. → `[ ] ` Enhanced `/rwr status` output with gate reporting.
7. → `[ ] ` Comprehensive test suite (unit, integration, regression).
8. → `[ ] ` OPERATIONS.md: state machine, gate tuning, troubleshooting.
9. → `[ ] ` RC cycle with gate and recovery testing.
10. → `[ ] ` Release v4.1.0.

### Path to v4.2.0 (polish, optional)
1. (v4.1.0 shipped)
2. → `[ ] ` Enhanced event payload design.
3. → `[ ] ` `/rwr validate` command.
4. → `[ ] ` Region import/export and `/rwr metrics`.
5. → `[ ] ` QoL commands (`/rwr config show`, `/rwr logs recent`).
6. → `[ ] ` Test and release v4.2.0.

---

## Summary: Phased Roadmap at a Glance

```
v3.x (CURRENT)
  ↓
v4.0.0 (Breaking Change MVP)
  • New config schema + migration
  • New command tree (/rwr)
  • Basic state machine & phase logging
  • MIGRATION.md + README updates
  • Target: Early adopters, testers
  ↓
v4.1.0 (Production Hardening) 
  • Deterministic resume on restart
  • Preflight gates (TPS, players, disk)
  • Enhanced /rwr status + /rwr metrics
  • Full test suite + OPERATIONS.md
  • Target: All production servers
  ↓
v4.2.0 (Polish & DX)
  • Enhanced event API
  • /rwr validate, /rwr metrics, region bulk ops
  • Target: Plugin developers & power users
  ↓
v5.0.0 (Future Architectural Refresh)
  • Remove v3 compat layer
  • Multi-world reset support
  • Database persistence option
  • Webhook/Discord notifications
  • Cross-server orchestration
  • GUI redesign
```

---

## Migration notes for server owners (v3 -> v4)

### What changes
- Commands move to `/rwr` subcommands.
- Config is auto-migrated to v4 schema on first startup.
- Reset recovery is more deterministic due to state machine phases.

### What is automatic
- Existing scheduling values are migrated from old keys to `schedule.*`.
- A backup of old config is written as `config.v3.backup.yml`.
- Old v3 reset-state.yml is archived with timestamp if found.

### What admins should verify after upgrade
1. **Update all scripts and automation** to use `/rwr` subcommands instead of old commands.
2. Update **permission nodes** if you have custom permission groups (old perms no longer work).
3. Run `/rwr status` and confirm expected world, schedule mode, next reset time, and **current phase**.
4. Run `/rwr next` and verify warning timing in your server timezone.
5. Check logs for `Migration completed` and no warnings about invalid keys.
6. Review **preflight gate settings** in config and adjust TPS/players/disk thresholds for your server.
7. Test a manual reset during low activity: `/rwr reset now` and monitor `/rwr status` phase progression.
8. If using region reset, run `/rwr region list` and validate entries.
9. Keep old config backup (`config.v3.backup.yml`) until first successful scheduled reset completes.

### Recommended upgrade path
1. **Stop server** and back up:
   - Full `plugins/ResourceWorldResetter/` folder
   - Target world files
   - Latest scheduled backup or snapshot
2. **Replace jar** with v4 release.
3. **Start server** and review migration logs:
   - Look for `[RWR] Migration completed` message
   - Check for warnings about unmapped config keys
   - Verify `config.v3.backup.yml` was created
4. **Execute validation commands**:
   - `/rwr status` – confirm world, schedule, phase, preflight gates
   - `/rwr next` – verify next reset time
   - `/rwr region list` – if applicable
5. **Update automation**:
   - Update any scripts from old commands to `/rwr` subcommands
   - Update cron jobs or external schedulers
6. **Tune preflight gates** based on your server profile (run `/rwr status` to see suggested values).
7. **Test manual reset** during low-activity window: `/rwr reset now`.
8. **Keep backups** until at least two successful scheduled resets complete.

### Rollback procedure
1. **Stop server immediately** if reset is in-progress.
2. **Restore previous v3 jar** from pre-upgrade backup.
3. **Restore config**: Rename `config.yml` to `config.v4.yml` for safekeeping, then rename `config.v3.backup.yml` to `config.yml`.
4. **Restore reset-state file**: Delete the v4 `reset-state.yml` file to allow v3 to start fresh (do **not** restore v3 reset-state format).
5. **Start server** and verify:
   - Old commands (`/rwrgui`, `/resetworld`, etc.) are available
   - Config scheduling values are correct
   - No migration logs appear
6. **Monitor logs** for any v3 errors and verify reset scheduling resumes normally.
7. If rollback fails, restore entire `plugins/ResourceWorldResetter/` folder from pre-upgrade backup.

## Suggested implementation order (for fastest safe delivery)
1. Config migration + tests.


## FAQ & Decision Guide

### Q: Should I release v4.0.0 or wait for v4.1.0?
**A**: Release v4.0.0 if:
- Config migration and new `/rwr` command tree are stable and tested.
- You want to introduce the breaking change and get early adopter feedback.
- You can wait 2–4 weeks for v4.1.0 to reach all production servers.

**Wait for v4.1.0 if**:
- You want preflight gates and deterministic recovery before shipping (safer choice for production).
- You want to ship the "complete" v4 experience.

**Recommendation**: Release v4.0.0 as "early adopter" version and v4.1.0 as "recommended for production."

### Q: Do I need v4.2.0?
**A**: No, v4.2.0 is optional polish. Skip it unless you:
- Run multiple worlds and want per-world scheduling.
- Develop third-party plugins that need richer event data.
- Want `/rwr validate` and `/rwr metrics` for operational insight.

### Q: What if v4.1.0 RC has issues?
**A**: Fix RC blockers and respin. If a blocker is serious, defer it to v4.1.1 patch release and ship v4.1.0 with a known issue + workaround note.

### Q: Can v4.0.0 admins upgrade to v4.1.0?
**A**: Yes. v4.1.0 auto-detects v4.0.0 state files, reads them, and resumes cleanly. No manual migration needed.

### Q: Should I keep v3 support in v4.x?
**A**: Yes, v4 should auto-migrate v3 config. Don't support v3 reset-state format (too risky). v5.0.0 is when you drop v3 compatibility entirely.

---

## Appendix: State Machine Diagram (ASCII)

```
IDLE (no reset in progress)
  │ (scheduled reset or /rwr reset now)
  ↓
PRECHECK (verify world exists, check preflight gates)
  │ (gates OK)
  ├─ (gate blocked) → DELAY/ABORT (retry at next scheduled time)
  ↓
TELEPORT (teleport players to safety world)
  │ (players teleported)
  ├─ (fail/timeout) → FAILED (mark failed, log reason)
  ↓
UNLOAD (unload world from memory)
  │ (world unloaded)
  ├─ (fail/stuck) → FAILED
  ↓
DELETE (delete world files)
  │ (files deleted)
  ├─ (permission/IO error) → FAILED
  ↓
RECREATE (regenerate world from seed/template)
  │ (world created)
  ├─ (fail/corruption) → FAILED
  ↓
VERIFY (verify world integrity, check for missing chunks)
  │ (verified OK)
  ├─ (fail/corrupt) → FAILED
  ↓
COMPLETE (reset successful, world loaded, players can rejoin)
  ↓
IDLE (ready for next reset)

FAILED (reset failed at some phase)
  │ (admin decision: retry, skip, manual recovery)
  ├─ (retry) → PRECHECK (retry from start or last safe phase)
  ├─ (skip) → IDLE (skip this reset, try at next scheduled time)
  ├─ (phase skip) → <next phase> (resume from specific phase; admin only)
  ↓
```

On server **restart**:
- If phase is IDLE or COMPLETE: start fresh next reset.
- If phase is PRECHECK—VERIFY: resume deterministically from last phase (v4.1.0+).
- If phase is FAILED: offer manual recovery options.

---

## Closing Thoughts

This roadmap balances:
- **Shipping speed**: v4.0.0 can ship quickly (breaking change MVP).
- **Production safety**: v4.1.0 adds gates and recovery.
- **Nice-to-haves**: v4.2.0 is optional polish.
- **Future flexibility**: v5.0.0 reserved for major changes (db, webhooks, etc.).

The key insight: **v4.0.0 is the breaking change; v4.1.0 is the production release.** This gives admins a clear upgrade path and lets you ship incrementally.


