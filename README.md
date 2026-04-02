# ResourceWorldResetter v3.4.0

<p align="center">
  <img src="https://files.catbox.moe/xhfveh.png" alt="project-image">
</p>

## Overview
ResourceWorldResetter automates the resetting of resource worlds on your Minecraft server. It integrates with **Multiverse-Core** to manage world regeneration without requiring restarts and provides an intuitive GUI for configuration.

## Features
- **Automated world resets** (daily, weekly, or monthly)
- **Selective region resets** — reset specific in-world regions without full world deletion
- **GUI-based** configuration
- **Admin-chosen target world** — no world is auto-created on install; use `/rwrgui` → *Change World* to pick any existing world
- **Multiverse-Core** support
- Safe teleportation before resets
- Configurable reset warnings
- **Async reset operations** — non-blocking world deletion and recreation to maintain server TPS
- **Robust scheduling** — debounced rescheduling and separate warning scheduling
- **Config validation** — automatic sanitization and error recovery for invalid settings
- **Region throttling** — batch chunk unloading (16/tick) to prevent main-thread blocking
- **bStats integration** — analytics with next-reset countdown chart
- **Custom Bukkit events** — `PreResetEvent`, `PostResetEvent`, `RegionPreResetEvent`, `RegionPostResetEvent` for third-party plugin integration
- **Reset failure recovery** — automatic retry with exponential backoff (30 s / 60 s / 120 s), admin alerts and recovery suggestions after 3 failed attempts
- **Graceful shutdown recovery** — if the server stops mid-reset, state is saved automatically and auto-resumed 60 s after the next startup (admins can use `/rwrresume` or `/rwrresume cancel` to control it)
- Supports **Minecraft 26.1.1+**

## Installation
1. **Download** the latest release from [GitHub Releases](https://github.com/Lozaine/ResourceWorldResetter/releases).
2. Place `ResourceWorldResetter.jar` into your `plugins/` folder.
3. Ensure **Multiverse-Core** is installed.
4. Restart your server.
5. Create the world you want to use as your resource world (e.g. via `/mv create Resources normal`).
6. Run `/rwrgui` → click **Change World** and select it. The plugin will now manage resets for that world.

## Commands & Permissions
| Command         | Description                                    | Permission                    |
|-----------------|------------------------------------------------|------------------------------|
| `/rwrgui`       | Open the configuration GUI                     | `resourceworldresetter.admin` |
| `/reloadrwr`    | Reload plugin configuration                    | `resourceworldresetter.admin` |
| `/resetworld`   | Manually reset the entire resource world       | `resourceworldresetter.admin` |
| `/rwrregion`    | Reset specific regions (GUI-based management)  | `resourceworldresetter.admin` |
| `/rwrresume`    | Immediately resume an incomplete reset detected on startup | `resourceworldresetter.admin` |
| `/rwrresume cancel` | Abort the pending auto-resume and dismiss the incomplete reset | `resourceworldresetter.admin` |

## More Information
For full documentation, including **detailed config settings, GUI breakdown, and advanced usage**, visit the **[Wiki](https://github.com/Lozaine/ResourceWorldResetter-v3.0.0/wiki)**.

---

**Author**: Lozaine  
GitHub: [Lozaine](https://github.com/Lozaine)  
Discord: [LozDev Mines](https://discord.gg/Y3UuG7xu9x)  

This project is licensed under the [MIT License](LICENSE).
