[CENTER][B][SIZE=6]ResourceWorldResetter v4.0.0[/SIZE][/B][/CENTER]

[CENTER][IMG]https://files.catbox.moe/i656bi.png[/IMG][/CENTER]

[CENTER][IMG]https://files.catbox.moe/we4uve.png[/IMG][/CENTER]

[CENTER][B]Admin GUI — `/rwr gui`[/B][/CENTER]

[CENTER]Click the icons in-game to change world, schedule, warnings, region settings, force resets, or reload config.[/CENTER]

[CENTER][B][SIZE=5]Automated Resource World Resets with Phase-Aware Status and Unified /rwr Commands[/SIZE][/B][/CENTER]

[B]ResourceWorldResetter v4[/B] is the next generation of automatic resource world resets. It introduces an explicit [B]phase-based reset model[/B], a unified [B]/rwr command tree[/B], and improved [B]operator visibility[/B] into reset progress and failures.

[B]⚠️ BREAKING CHANGE:[/B] v4 introduces a new unified `/rwr` command structure. Old commands (`/rwrgui`, `/resetworld`, `/rwrregion`, `/rwrresume`, etc.) no longer exist. Server owners must update scripts and permissions. Auto-migration is provided for configs.

[B][SIZE=5]What's New in v4[/SIZE][/B]

[B][COLOR=#00ff00]✓ Explicit Reset Phases[/COLOR][/B]
- Reset progress is tracked using phases: IDLE → PRECHECK → TELEPORT → UNLOAD → DELETE → RECREATE → VERIFY → COMPLETE (or FAILED on error)
- `/rwr status` reports the exact phase for in-progress or failed resets
- Reset state metadata is persisted for better operational visibility

[B][COLOR=#00ff00]✓ Unified Command Structure[/COLOR][/B]
- All functionality is grouped under `/rwr` subcommands
- Built-in help: `/rwr` or `/rwr help`
- Legacy command aliases are intentionally removed in v4

Built-in help: `/rwr help` now shows a complete, command-by-command help page with usage examples and permission notes for every subcommand.

[B][COLOR=#00ff00]✓ Full Operator Visibility[/COLOR][/B]
- `/rwr status` — shows exact phase, next reset time, resume phase, and last failure reason
- `/rwr next` — displays next reset timestamp, warning timestamp, and countdown in server timezone
- No more guessing whether a reset is stuck, blocked, or progressing

[B][SIZE=5]Key Features[/SIZE][/B]

[B]Core Features:[/B]
- [B]Automatic Resource World Resets[/B] — daily, weekly, monthly schedules
- [B]Selective Region Resets[/B] — reset specific areas without deleting the entire world
- [B]GUI-Based Configuration[/B] — no manual YAML editing needed
- [B]Multiverse-Core Integration[/B] — handles world deletion and recreation

[B]Safety & Reliability:[/B]
- [B]State Machine Foundation[/B] — explicit phases and persisted state tracking
- [B]Failure Recovery Controls[/B] — resume and cancel controls for incomplete reset handling
- [B]Failure Visibility[/B] — phase and failure details exposed in status output
- [B]Safe Player Teleportation[/B] — guarantees solid ground; automatic fallback if no safe spot exists
- [B]Graceful Shutdown Handling[/B] — incomplete reset state is detected and can be managed with `/rwr resume`

[B]Operator Experience:[/B]
- [B]Phase-Level Status Reporting[/B] — `/rwr status` shows exact phase, not just "IN_PROGRESS"
- [B]Reset Scheduling Transparency[/B] — `/rwr next` shows countdown to next reset
- [B]Recovery Options[/B] — `/rwr resume` and `/rwr resume cancel` for interrupted resets

[B]Player Experience:[/B]
- [B]World Teleport GUI[/B] — `/rwr tp` for easy world navigation; `/rwr back` to return
- [B]Configurable Warnings[/B] — countdown broadcasts before scheduled resets
- [B]Region Throttling[/B] — batch chunk unloading prevents lag spikes
- [B]Custom Events[/B] — plugin integration via PreResetEvent, PostResetEvent, RegionPreResetEvent, RegionPostResetEvent

[B][SIZE=5]v4.0.0 Breaking Changes[/SIZE][/B]

[COLOR=#ff9900][B]Old commands no longer exist:[/B][/COLOR]
- `/rwrgui` → use `/rwr gui`
- `/reloadrwr` → use `/rwr reload`
- `/resetworld` → use `/rwr reset now`
- `/rwrregion` → use `/rwr region <enable|disable|list|add|remove|addhere>`
- `/rwrresume` → use `/rwr resume [cancel]`

[COLOR=#ff9900][B]What is automatic:[/B][/COLOR]
- Config is auto-migrated from v3 schema to v4 on first startup
- A backup is created as `config.v3.backup.yml`
- Old reset-state format is archived

[COLOR=#ff9900][B]What admins must do:[/B][/COLOR]
1. Update all scripts and automation to `/rwr` subcommands
2. Update permission nodes in your permission plugin
3. Run `/rwr status` to verify setup
4. Test with `/rwr reset now` during low activity

See **[MIGRATION.md](https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/blob/master/MIGRATION.md)** for the full upgrade guide.

[B][SIZE=5]Commands & Permissions[/SIZE][/B]

| [B]Command[/B] | [B]Description[/B] | [B]Permission[/B] |
|---|---|---|
| /rwr help | Show all supported `/rwr` subcommands | resourceworldresetter.admin |
| /rwr gui | Open the admin configuration GUI | resourceworldresetter.admin |
| /rwr reload | Reload config from file | resourceworldresetter.admin |
| /rwr status | Show current phase, next reset, and failure/resume details | resourceworldresetter.admin |
| /rwr next | Show next reset timestamp, warning timestamp, and countdown | resourceworldresetter.admin |
| /rwr reset now | Force an immediate reset | resourceworldresetter.admin |
| /rwr resume [cancel] | Resume or cancel incomplete reset recovery | resourceworldresetter.admin |
| /rwr region <enable\|disable\|list\|add\|remove\|addhere> | Manage region-based resets | resourceworldresetter.admin |
| /rwr tp | Open world teleport menu | resourceworldresetter.admin |
| /rwr back | Teleport back to previous location | resourceworldresetter.admin |

## Permissions

The plugin exposes granular permission nodes for player-facing teleport commands so servers can allow them to non-admin players while keeping admin operations restricted.

- `resourceworldresetter.tp` — Allows `/rwr tp` to open the world selection GUI. Default: granted to all players.
- `resourceworldresetter.back` — Allows `/rwr back` to teleport back to your last recorded location. Default: granted to all players.

Admin operations remain protected by `resourceworldresetter.admin` (default: op).

[B][SIZE=5]Configuration (v4 Schema)[/SIZE][/B]

[code=YAML]
configVersion: 4                    # Schema version
worldName: "Resources"              # Target resource world

schedule:
  mode: "daily"                     # daily, weekly, or monthly
  time:
    hour: 3                         # 24-hour format (0-23)
  warningMinutes: 5                 # Minutes before reset for warnings
  day: 1                            # For weekly: 1-7 (Mon-Sun); For monthly: 1-31

preflight:                          # Reserved for v4.1+ production hardening
  tps:
    min: 15.0                       # Abort if TPS drops below this (0 = disabled)
    onFail: "delay"                 # delay, abort, or force
  players:
    max: 0                          # Abort if more players online (0 = disabled)
    onFail: "delay"
  disk:
    freeGB: 5.0                     # Abort if free disk falls below this (0 = disabled)
    onFail: "delay"

regions:
  enabled: false                    # Enable selective region resets
  # populated via /rwr region add

metrics:
  enabled: false                    # Optional anonymous telemetry
[/code]

[B][SIZE=5]Supported Versions[/SIZE][/B]
- [B]Minecraft:[/B] 1.20.5+ (Spigot API 26.1+)
- [B]Java:[/B] 25 or newer
- [B]Multiverse-Core:[/B] 4.3.1+ (fully compatible with v5)
- [B]Tested on:[/B] Spigot 26.1, 26.1.1, 26.1.2

[B][SIZE=5]Installation[/SIZE][/B]
1. [B]Download[/B] the latest v4 release from [URL='https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/releases']GitHub Releases[/URL]
2. [B]Back up[/B] your plugin folder and worlds
3. Place [B]ResourceWorldResetter.jar[/B] into `plugins/`
4. Ensure [B]Multiverse-Core 4.3.1+[/B] is installed
5. [B]Restart[/B] your server (auto-migration will run if upgrading from v3)
6. Create your resource world (e.g., `/mv create Resources normal`)
7. Run `/rwr gui` and click [B]Change World[/B] to select it
8. Configure schedule and preflight gates via GUI or `config.yml`

[B][SIZE=5]How It Works[/SIZE][/B]
1. Admin configures target world and reset schedule via `/rwr gui`
2. On scheduled reset, plugin enters phase-based flow: PRECHECK → TELEPORT → UNLOAD → DELETE → RECREATE → VERIFY → COMPLETE
4. Players are safely teleported to a fallback world with visual warnings
5. World is deleted and regenerated by Multiverse-Core
6. Status is visible at any time via `/rwr status` (exact phase reported)
7. If reset fails, admins can inspect failure reason and control recovery with `/rwr resume`

[B][SIZE=5]Important Information[/SIZE][/B]

[COLOR=#ff9900][B]Time Zone Awareness:[/B][/COLOR] The plugin schedules resets based on the server's local system time.

[COLOR=#ff9900][B]Data Loss Warning:[/B][/COLOR] Once a world is reset, [B]all data within it is permanently deleted[/B], including player structures and inventories. Back up your world files before configuring and using this plugin.

[COLOR=#ff9900][B]Preflight Gates:[/B][/COLOR] v4.0.0 includes preflight configuration keys (TPS, players, disk) in the schema for forward compatibility. Full gate enforcement and policy controls are targeted for v4.1+.

[COLOR=#ff9900][B]Reset Phases:[/B][/COLOR] Monitor `/rwr status` to see which phase the current or last reset reached. Phase information is essential for troubleshooting reset failures.

[B][SIZE=5]Support & Documentation[/SIZE][/B]

[B]📖 Full Guides:[/B]
- **[README](https://github.com/TamaWish/ResourceWorldResetter-v3.0.0)** — overview, features, and configuration

[B]🐛 Issues & Support:[/B]
- Report bugs: [URL='https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/issues']GitHub Issues[/URL]
- Join Discord: [URL='https://discord.gg/Y3UuG7xu9x']LozDev Mines[/URL]
- Contributions welcome: Pull requests accepted

[B][SIZE=5]Author & License[/SIZE][/B]
[B]Author:[/B] Lozaine
[B]GitHub:[/B] [URL='https://github.com/Lozaine']Lozaine[/URL]
[B]Discord:[/B] [URL='https://discord.gg/Y3UuG7xu9x']LozDev Mines[/URL]
[B]License:[/B] [URL='https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/blob/master/LICENSE']MIT License[/URL]

[CENTER][B][SIZE=5]Download v4.0.0 Now — Breaking Change Release with Better Commands and Phase Visibility[/SIZE][/B][/CENTER]