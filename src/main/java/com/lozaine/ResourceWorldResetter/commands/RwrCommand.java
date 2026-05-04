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

                boolean teleported = player.teleport(previousLocation);
                if (teleported) {
                    sender.sendMessage(ChatColor.GREEN + "[RWR] Teleported back to your previous location.");
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
        sender.sendMessage(ChatColor.GOLD + "/rwr gui" + ChatColor.GRAY + " - Open the admin GUI");
        sender.sendMessage(ChatColor.GOLD + "/rwr reload" + ChatColor.GRAY + " - Reload plugin configuration");
        sender.sendMessage(ChatColor.GOLD + "/rwr reset now" + ChatColor.GRAY + " - Force an immediate reset");
        sender.sendMessage(ChatColor.GOLD + "/rwr resume [cancel]" + ChatColor.GRAY + " - Resume or cancel incomplete reset recovery");
        sender.sendMessage(ChatColor.GOLD + "/rwr tp" + ChatColor.GRAY + " - Open world selection menu (shows all worlds including Nether & End)");
        sender.sendMessage(ChatColor.GOLD + "/rwr back" + ChatColor.GRAY + " - Teleport back to your previous location");
        sender.sendMessage(ChatColor.GOLD + "/rwr region <enable|disable|list|add|remove|addhere>" + ChatColor.GRAY + " - Manage region resets");
        sender.sendMessage(ChatColor.GOLD + "/rwr status" + ChatColor.GRAY + " - Show reset state and schedule info");
        sender.sendMessage(ChatColor.GOLD + "/rwr next" + ChatColor.GRAY + " - Show next reset and warning timestamps");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "[RWR] Status");
        sender.sendMessage(ChatColor.GRAY + "State: " + describeState());
        sender.sendMessage(ChatColor.GRAY + "Current flow: " + (plugin.isCurrentResetRegionReset() ? "region reset" : "full reset"));
        sender.sendMessage(ChatColor.GRAY + "Resume phase: " + plugin.getIncompleteResetResumePhase());
        sender.sendMessage(ChatColor.GRAY + "Schedule: " + describeSchedule());

        LocalDateTime next = plugin.getNextResetInstant();
        if (next != null) {
            sender.sendMessage(ChatColor.GRAY + "Next reset: " + formatTimestamp(next));
        } else {
            sender.sendMessage(ChatColor.GRAY + "Next reset: unavailable");
        }

        sender.sendMessage(ChatColor.GRAY + "Retry attempt: " + plugin.getResetAttempt());
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

        LocalDateTime warning = next.minusMinutes(plugin.getResetWarningTime());
        sender.sendMessage(ChatColor.AQUA + "[RWR] Next reset: " + formatTimestamp(next));
        sender.sendMessage(ChatColor.AQUA + "[RWR] Warning time: " + formatTimestamp(warning));
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