# ResourceWorldResetter v4.0.0

Release v4.0.0 — Breaking change: unified `/rwr` command tree and phase-aware reset state.

Highlights:
- New `/rwr` subcommand tree (legacy aliases removed).
- Phase-based state machine with persisted reset state and `/rwr status` visibility.
- Built-in in-game help: `/rwr help` shows detailed usage, examples, and permission notes.
- Config schema v4 with automatic migration from v3 (backup created as `config.v3.backup.yml`).

Breaking changes:
- Old commands such as `/rwrgui`, `/resetworld`, `/rwrregion`, `/rwrresume` are no longer registered.

Upgrade notes:
1. Back up your plugin and world folders.
2. Replace the jar with v4 and restart — migration runs automatically.
3. Update scripts and permissions to use `/rwr` subcommands.
4. Verify with `/rwr status` and test with `/rwr reset now` during maintenance.

Full migration and troubleshooting guide: see MIGRATION.md and OPERATIONS.md.
