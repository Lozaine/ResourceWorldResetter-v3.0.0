package com.lozaine.ResourceWorldResetter.commands;

import com.lozaine.ResourceWorldResetter.ResetPhase;
import com.lozaine.ResourceWorldResetter.ResourceWorldResetter;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RwrCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "gui",
            "reload",
            "reset",
            "resume",
            "tp",
            "back",
            "region",
            "status",
            "next"
    );

    private static final List<String> RESET_SUBCOMMANDS = List.of("now");
    private static final List<String> RESUME_SUBCOMMANDS = List.of("cancel");
    private static final List<String> REGION_SUBCOMMANDS = List.of(
            "enable",
            "disable",
            "list",
            "add",
            "remove",
            "addhere"
    );

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final ResourceWorldResetter plugin;
    private final RwrRegionCommand regionCommand;

    public RwrCommand(ResourceWorldResetter plugin) {
        this.plugin = plugin;
        this.regionCommand = new RwrRegionCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("resourceworldresetter.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);

        return dispatch(sender, subcommand, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("resourceworldresetter.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return ROOT_SUBCOMMANDS;
        }

        if (args.length == 1) {
            return prefixMatches(ROOT_SUBCOMMANDS, args[0]);
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);

        return switch (root) {
            case "reset" -> completeReset(tail);
            case "resume" -> completeResume(tail);
            case "region" -> completeRegion(tail);
            default -> Collections.emptyList();
        };
    }

    private boolean dispatch(CommandSender sender, String subcommand, String[] args) {
        return switch (subcommand) {
            case "help", "?" -> {
                sendHelp(sender);
                yield true;
            }
            case "gui" -> {
                if (sender instanceof Player player) {
                    plugin.openAdminGui(player);
                    yield true;
                }
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                yield true;
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.loadConfig();
                sender.sendMessage(ChatColor.GREEN + "[RWR] Configuration reloaded.");
                yield true;
            }
            case "reset" -> {
                if (args.length == 0 || !"now".equalsIgnoreCase(args[0])) {
                    sender.sendMessage(ChatColor.GOLD + "Usage: /rwr reset now");
                    yield true;
                }
                sender.sendMessage(ChatColor.GREEN + "[RWR] Forcing resource world reset...");
                plugin.resetResourceWorld(false);
                yield true;
            }
            case "resume" -> {
                if (args.length > 0 && "cancel".equalsIgnoreCase(args[0])) {
                    plugin.cancelAutoResumeTask();
                    plugin.clearIncompleteResetState();
                    sender.sendMessage(ChatColor.GREEN + "[RWR] Incomplete reset auto-resume cancelled and state cleared.");
                    plugin.getLogger().info(sender.getName() + " cancelled the incomplete reset auto-resume");
                    yield true;
                }

                if (plugin.getResetStateFile() == null || !plugin.getResetStateFile().exists()) {
                    sender.sendMessage(ChatColor.YELLOW + "[RWR] No incomplete reset state detected.");
                    yield true;
                }

                org.bukkit.configuration.file.YamlConfiguration state = org.bukkit.configuration.file.YamlConfiguration
                        .loadConfiguration(plugin.getResetStateFile());
                boolean wasRegionReset = state.getBoolean("regionReset", false);
                ResetPhase resumePhase = plugin.getIncompleteResetResumePhase();
                sender.sendMessage(ChatColor.GREEN + "[RWR] Resuming incomplete " +
                        (wasRegionReset ? "region" : "full") + " reset for world '" + plugin.getWorldName() + "' at phase " + resumePhase + "...");
                plugin.getLogger().info(sender.getName() + " manually triggered resume of incomplete reset at phase " + resumePhase);
                plugin.resumeIncompleteResetFromCommand(wasRegionReset, resumePhase);
                yield true;
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                    yield true;
                }

                plugin.getTeleportGUI().openWorldSelectionMenu(player);
                sender.sendMessage(ChatColor.GREEN + "[RWR] Select a world to teleport to.");
                yield true;
            }
            case "back" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                    yield true;
                }

                if (!plugin.getTeleportationSystem().hasPreviousLocation(player)) {
                    sender.sendMessage(ChatColor.YELLOW + "[RWR] No previous location recorded.");
                    yield true;
                }

                Location previousLocation = plugin.getTeleportationSystem().getPreviousLocation(player);
                if (previousLocation == null || previousLocation.getWorld() == null) {
                    sender.sendMessage(ChatColor.RED + "[RWR] Previous location is no longer available.");
                    yield true;
                }

                ResourceWorldResetter.BackTeleportDestination destination =
                        plugin.resolveBackTeleportDestination(player, previousLocation);
                Location targetLocation = destination.location();

                boolean teleported = player.teleport(targetLocation);
                if (teleported) {
                    if (destination.redirectedBecauseReset()) {
                        if (destination.villageTargeted()) {
                            sender.sendMessage(ChatColor.GREEN + "[RWR] Resource world was reset since your last location. Teleported to a nearby village instead.");
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "[RWR] Resource world was reset since your last location. Teleported to safe world spawn instead.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "[RWR] Teleported back to your previous location.");
                    }
                    plugin.getTeleportationSystem().clearPlayerLocation(player);
                } else {
                    sender.sendMessage(ChatColor.RED + "[RWR] Failed to teleport back.");
                }
                yield true;
            }
            case "region" -> {
                yield regionCommand.handle(sender, args);
            }
            case "status" -> {
                sendStatus(sender);
                yield true;
            }
            case "next" -> {
                sendNextReset(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /rwr for help.");
                yield true;
            }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "[RWR] Commands:");
        sender.sendMessage(ChatColor.GOLD + "/rwr gui" + ChatColor.GRAY + " - Open the admin configuration GUI. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr reload" + ChatColor.GRAY + " - Reload configuration from disk. Use after manual edits. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr reset now" + ChatColor.GRAY + " - Force an immediate full reset of the configured resource world. Use with caution; this is destructive. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr resume" + ChatColor.GRAY + " - Resume an incomplete reset detected on disk (if any). Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr resume cancel" + ChatColor.GRAY + " - Cancel auto-resume and clear incomplete reset state. Use when you want to abort recovery. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr tp" + ChatColor.GRAY + " - Open a world selection GUI to teleport to available worlds. Player-only. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr back" + ChatColor.GRAY + " - Teleport back to your previous location recorded by RWR. Player-only. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr region list" + ChatColor.GRAY + " - List configured regions eligible for selective resets. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr region enable|disable <region>" + ChatColor.GRAY + " - Enable or disable region-based resets. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr region add <name> <x1> <z1> <x2> <z2>" + ChatColor.GRAY + " - Add a new named region (or use addhere). Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr region remove <name>" + ChatColor.GRAY + " - Remove a named region. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr region addhere <name>" + ChatColor.GRAY + " - Create a region using your current selection/position. Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr status" + ChatColor.GRAY + " - Show detailed reset state, current phase, next scheduled reset, and last failure (if any). Permission: resourceworldresetter.admin");
        sender.sendMessage(ChatColor.GOLD + "/rwr next" + ChatColor.GRAY + " - Show next scheduled reset timestamp, warning time, and countdown. Permission: resourceworldresetter.admin");

        sender.sendMessage(ChatColor.GRAY + "Tips:");
        sender.sendMessage(ChatColor.GRAY + " - Use /rwr help to show this page.");
        sender.sendMessage(ChatColor.GRAY + " - After upgrading from v3, check /rwr status and run /rwr reload if you modified config.yml.");
        sender.sendMessage(ChatColor.GRAY + "Examples:");
        sender.sendMessage(ChatColor.DARK_AQUA + " - /rwr reset now" + ChatColor.GRAY + " (force reset during maintenance window)");
        sender.sendMessage(ChatColor.DARK_AQUA + " - /rwr resume" + ChatColor.GRAY + " (resume an interrupted reset)");
        sender.sendMessage(ChatColor.DARK_AQUA + " - /rwr region list" + ChatColor.GRAY + " (see configured region names)");
    }

    private void sendStatus(CommandSender sender) {
        ResetPhase currentPhase = plugin.getResetPhase();
        ResetPhase failedPhase = plugin.getFailedResetPhase();
        ResetPhase resumePhase = plugin.getIncompleteResetResumePhase();

        sender.sendMessage(ChatColor.AQUA + "[RWR] Status");
        sender.sendMessage(ChatColor.GRAY + "State: " + describeState());
        sender.sendMessage(ChatColor.GRAY + "Current phase: " + formatPhase(currentPhase));
        sender.sendMessage(ChatColor.GRAY + "Failed phase: " + formatPhase(failedPhase));
        sender.sendMessage(ChatColor.GRAY + "Resume phase: " + formatPhase(resumePhase));
        sender.sendMessage(ChatColor.GRAY + "Reset started: " + formatEpochMillis(plugin.getResetStartedAtMillis()));
        sender.sendMessage(ChatColor.GRAY + "Phase started: " + formatEpochMillis(plugin.getResetPhaseStartedAtMillis()));
        sender.sendMessage(ChatColor.GRAY + "Phase elapsed: " + formatDuration(plugin.getCurrentPhaseElapsedMillis()));
        sender.sendMessage(ChatColor.GRAY + "Phase total: " + formatDuration(plugin.getAccumulatedPhaseDurationMillis(currentPhase)));
        sender.sendMessage(ChatColor.GRAY + "Current flow: " + (plugin.isCurrentResetRegionReset() ? "region reset" : "full reset"));
        sender.sendMessage(ChatColor.GRAY + "Schedule: " + describeSchedule());

        LocalDateTime next = plugin.getNextResetInstant();
        if (next != null) {
            sender.sendMessage(ChatColor.GRAY + "Next reset: " + formatTimestamp(next));
        } else {
            sender.sendMessage(ChatColor.GRAY + "Next reset: unavailable");
        }

        sender.sendMessage(ChatColor.GRAY + "Retry attempt: " + plugin.getResetAttempt() + "/" + plugin.getMaxResetAttempts());
        sender.sendMessage(ChatColor.GRAY + "Last failure: " + describeFailure());
        sender.sendMessage(ChatColor.GRAY + "Auto-resume queued: " + (plugin.isAutoResumeQueued() ? ChatColor.GREEN + "yes" : ChatColor.YELLOW + "no"));
        sender.sendMessage(ChatColor.GRAY + "Reset state file: " + (plugin.getResetStateFile() != null && plugin.getResetStateFile().exists() ? "present" : "absent"));
    }

    private void sendNextReset(CommandSender sender) {
        LocalDateTime next = plugin.getNextResetInstant();
        if (next == null) {
            sender.sendMessage(ChatColor.YELLOW + "[RWR] Next reset is not currently scheduled.");
            return;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime warning = next.minusMinutes(plugin.getResetWarningTime());
        sender.sendMessage(ChatColor.AQUA + "[RWR] Next reset: " + formatTimestamp(next));
        sender.sendMessage(ChatColor.AQUA + "[RWR] Warning time: " + formatTimestamp(warning));
        sender.sendMessage(ChatColor.AQUA + "[RWR] Time remaining: " + formatCountdown(Duration.between(now, next)));
        sender.sendMessage(ChatColor.AQUA + "[RWR] Server timezone: " + zone);
    }

    private String describeSchedule() {
        String mode = plugin.getResetType().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "weekly" -> {
                int day = plugin.getResetDay();
                String dayLabel = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                yield "weekly on " + dayLabel + " (" + day + ") at " + String.format("%02d:00", plugin.getRestartTime()) + " with " + plugin.getResetWarningTime() + " minute warning";
            }
            case "monthly" -> "monthly on day " + plugin.getResetDay() + " at " + String.format("%02d:00", plugin.getRestartTime()) + " with " + plugin.getResetWarningTime() + " minute warning";
            default -> "daily at " + String.format("%02d:00", plugin.getRestartTime()) + " with " + plugin.getResetWarningTime() + " minute warning";
        };
    }

    private String describeFailure() {
        String reason = plugin.getLastResetFailureReason();
        if (reason == null || reason.isBlank()) {
            return "none recorded";
        }

        String detail = plugin.getLastResetFailureDetail();
        if (detail == null || detail.isBlank()) {
            return reason;
        }

        String stackTrace = plugin.getLastResetFailureStackTrace();
        if (stackTrace == null || stackTrace.isBlank()) {
            return reason + " (" + detail + ")";
        }

        return reason + " (" + detail + ")";
    }

    private String describeState() {
        ResetPhase phase = plugin.getResetPhase();
        if (plugin.isResetInProgress()) {
            return ChatColor.RED + "IN_PROGRESS: " + phase;
        }
        if (phase == ResetPhase.FAILED) {
            ResetPhase failedPhase = plugin.getFailedResetPhase();
            return ChatColor.DARK_RED + "FAILED: " + (failedPhase == null ? "unknown" : failedPhase.name());
        }
        return ChatColor.GREEN + phase.name();
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        ZoneId zone = ZoneId.systemDefault();
        return timestamp.atZone(zone).format(TIMESTAMP_FORMAT);
    }

    private String formatEpochMillis(long epochMillis) {
        if (epochMillis <= 0L) {
            return "n/a";
        }
        return formatTimestamp(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }

    private String formatDuration(long durationMillis) {
        if (durationMillis <= 0L) {
            return "0s";
        }

        long totalSeconds = durationMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    private String formatCountdown(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "due now";
        }

        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private String formatPhase(ResetPhase phase) {
        if (phase == null) {
            return "n/a";
        }
        return phase.name();
    }

    

    private List<String> completeReset(String[] args) {
        if (args.length == 0) {
            return RESET_SUBCOMMANDS;
        }
        if (args.length == 1) {
            return prefixMatches(RESET_SUBCOMMANDS, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> completeResume(String[] args) {
        if (args.length == 0) {
            return RESUME_SUBCOMMANDS;
        }
        if (args.length == 1) {
            return prefixMatches(RESUME_SUBCOMMANDS, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> completeRegion(String[] args) {
        if (args.length == 0) {
            return REGION_SUBCOMMANDS;
        }
        if (args.length == 1) {
            return prefixMatches(REGION_SUBCOMMANDS, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> prefixMatches(List<String> options, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return options;
        }

        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lowerPrefix)) {
                matches.add(option);
            }
        }
        return matches;
    }
}