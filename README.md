# ResourceWorldResetter v4.0.0

<p align="center">
  <img src="https://files.catbox.moe/xhfveh.png" alt="project-image">
</p>

## Overview
ResourceWorldResetter automates resource-world resets on a Minecraft server. It integrates with **Multiverse-Core** to handle world regeneration without server restarts and includes an admin GUI for day-to-day configuration. Multiverse-Core v5 is supported through the world bridge.

## Features
- **Automated world resets** (daily, weekly, or monthly)
- **Selective region resets** — reset specific in-world regions without full world deletion
- **GUI-based** configuration
 - **Admin-chosen target world** — no world is auto-created on install; use `/rwr gui` → *Change World* to pick any existing world
- **Multiverse-Core** support
- **Safe teleportation** — guarantees players land on solid ground when entering any world; automatically finds safe landing spots and falls back gracefully if none exist nearby
- **World teleport GUI** — `/rwr tp` opens a menu to select and teleport to any world; `/rwr back` returns to your previous location
- Configurable reset warnings
- **Async reset operations** — non-blocking world deletion and recreation to maintain server TPS
- **Robust scheduling** — debounced rescheduling and separate warning scheduling
- **Config validation** — automatic sanitization and error recovery for invalid settings
- **Region throttling** — batch chunk unloading (16/tick) to prevent main-thread blocking
- **Analytics integration** — optional telemetry can be enabled in `config.yml`
- **Custom Bukkit events** — `PreResetEvent`, `PostResetEvent`, `RegionPreResetEvent`, `RegionPostResetEvent` for third-party plugin integration
- **Reset failure recovery** — automatic retry with exponential backoff (30 s / 60 s / 120 s), admin alerts and recovery suggestions after 3 failed attempts
- **Graceful shutdown recovery** — if the server stops mid-reset, state is saved automatically and auto-resumed 60 s after the next startup (admins can use `/rwr resume` or `/rwr resume cancel` to control it)
- Requires **Java 21 or newer** and a server compatible with **Spigot API 26.1+** (tested on 26.1, 26.1.1, and 26.1.2)

## Installation
1. **Download** the latest release from [GitHub Releases](https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/releases).
2. Place `ResourceWorldResetter.jar` into your `plugins/` folder.
3. Ensure **Multiverse-Core** is installed.
4. Back up your worlds and plugin configuration before first use.
5. Make sure you are running **Java 25 or newer**.
6. Restart your server.
7. Create the world you want to use as your resource world (e.g. via `/mv create Resources normal`).
8. Run `/rwr gui` and click **Change World** to select it. The plugin will then manage resets for that world.

## Commands & Permissions
| Command         | Description                                    | Permission                    |
|-----------------|------------------------------------------------|------------------------------|
| `/rwr`          | Root command. Subcommands: `gui`, `reload`, `reset now`, `resume [cancel]`, `tp`, `back`, `region <enable|disable|list|add|remove|addhere>`, `status`, `next` | `resourceworldresetter.admin` |

Use `/rwr gui` to open the admin GUI. `/rwr region addhere` adds the region you are currently standing in (useful when you want to mark a region without typing coordinates).

## More Information
For full documentation, including **detailed config settings, GUI breakdown, and advanced usage**, visit the **[Wiki](https://github.com/TamaWish/ResourceWorldResetter-v3.0.0/wiki)**.

---

**Author**: Lozaine  
GitHub: [Lozaine](https://github.com/Lozaine)  
Discord: [LozDev Mines](https://discord.gg/Y3UuG7xu9x)  

This project is licensed under the [MIT License](LICENSE).
