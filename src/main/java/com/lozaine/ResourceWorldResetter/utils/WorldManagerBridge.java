package com.lozaine.ResourceWorldResetter.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Bridges world management between Bukkit and optionally Multiverse-Core.
 * Uses reflection so there is no compile-time or runtime hard dependency on MV.
 *
 * <ul>
 *   <li>No MV installed    → pure Bukkit API</li>
 *   <li>MV 4.x installed   → MV4 API (world registered in MV's database)</li>
 *   <li>MV 5.x+ installed  → Bukkit creation + best-effort MV5 import</li>
 * </ul>
 */
public class WorldManagerBridge {

    public enum MVVersion { NONE, MV4, MV5_PLUS }

    private final JavaPlugin host;
    private final MVVersion mvVersion;
    private final Plugin mvPlugin;

    public WorldManagerBridge(JavaPlugin host) {
        this.host = host;
        Plugin detected = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (detected == null) {
            this.mvPlugin = null;
            this.mvVersion = MVVersion.NONE;
            LogUtil.log(host.getLogger(),
                    "Multiverse-Core not detected; using native Bukkit world management.", Level.INFO);
        } else {
            String className = detected.getClass().getName();
            if (className.startsWith("com.onarandombox.MultiverseCore")) {
                this.mvVersion = MVVersion.MV4;
                this.mvPlugin = detected;
                LogUtil.log(host.getLogger(),
                        "Detected Multiverse-Core 4.x – world ops will use MV4 API.", Level.INFO);
            } else {
                // MV 5.x uses org.mvplugins.multiverse.core or similar
                this.mvVersion = MVVersion.MV5_PLUS;
                this.mvPlugin = detected;
                LogUtil.log(host.getLogger(),
                        "Detected Multiverse-Core 5.x – using Bukkit for world ops with best-effort MV registration.", Level.INFO);
            }
        }
    }

    public MVVersion getMVVersion() { return mvVersion; }
    public boolean isMultiverseAvailable() { return mvVersion != MVVersion.NONE; }

    /**
     * Unloads the world; deregisters from MV4 when available, otherwise uses Bukkit.
     */
    public boolean unloadWorld(String worldName) {
        if (mvVersion == MVVersion.MV4) {
            try {
                Object wm = mvPlugin.getClass().getMethod("getMVWorldManager").invoke(mvPlugin);

                // Try two-arg overload first (name, boolean)
                Method twoArg = findMethod(wm.getClass(), "unloadWorld", String.class, boolean.class);
                if (twoArg != null) {
                    Boolean result = (Boolean) twoArg.invoke(wm, worldName, true);
                    if (Boolean.TRUE.equals(result)) {
                        LogUtil.log(host.getLogger(), "MV4: unloaded world '" + worldName + "'", Level.INFO);
                        return true;
                    }
                }

                // Fall back to single-arg overload
                Method oneArg = findMethod(wm.getClass(), "unloadWorld", String.class);
                if (oneArg != null) {
                    Boolean result = (Boolean) oneArg.invoke(wm, worldName);
                    if (Boolean.TRUE.equals(result)) {
                        LogUtil.log(host.getLogger(), "MV4: unloaded world '" + worldName + "' (single-arg)", Level.INFO);
                        return true;
                    }
                }

                LogUtil.log(host.getLogger(),
                        "MV4 unloadWorld returned false; falling back to Bukkit.", Level.WARNING);
            } catch (Exception e) {
                LogUtil.log(host.getLogger(),
                        "MV4 unloadWorld error; falling back to Bukkit: " + e.getMessage(), Level.WARNING);
            }
        }

        // MV5 or standalone – Bukkit handles the unload
        boolean result = Bukkit.unloadWorld(worldName, true);
        LogUtil.log(host.getLogger(), "Bukkit: unloadWorld('" + worldName + "') = " + result, Level.INFO);
        return result;
    }

    /**
     * Creates the world; registers with MV when available.
     */
    public boolean createWorld(String worldName) {
        if (mvVersion == MVVersion.MV4) {
            try {
                Object wm = mvPlugin.getClass().getMethod("getMVWorldManager").invoke(mvPlugin);
                Method addWorld = findMethod(wm.getClass(), "addWorld",
                        String.class, World.Environment.class, String.class,
                        WorldType.class, Boolean.class, String.class);
                if (addWorld != null) {
                    Boolean result = (Boolean) addWorld.invoke(
                            wm, worldName, World.Environment.NORMAL, null, WorldType.NORMAL, true, "DEFAULT");
                    if (Boolean.TRUE.equals(result)) {
                        LogUtil.log(host.getLogger(),
                                "MV4: created and registered world '" + worldName + "'", Level.INFO);
                        return true;
                    }
                }
                LogUtil.log(host.getLogger(),
                        "MV4 addWorld returned false; falling back to Bukkit.", Level.WARNING);
            } catch (Exception e) {
                LogUtil.log(host.getLogger(),
                        "MV4 addWorld error; falling back to Bukkit: " + e.getMessage(), Level.WARNING);
            }
        }

        // Bukkit creation (MV5 or standalone)
        World created = new WorldCreator(worldName)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .createWorld();
        if (created == null) {
            LogUtil.log(host.getLogger(), "Bukkit: createWorld('" + worldName + "') failed.", Level.SEVERE);
            return false;
        }
        LogUtil.log(host.getLogger(), "Bukkit: created world '" + worldName + "'", Level.INFO);

        if (mvVersion == MVVersion.MV5_PLUS) {
            tryMV5Import(worldName);
        }
        return true;
    }

    /**
     * Best-effort attempt to import the already-loaded world into MV5's registry after Bukkit creation.
     * Silently skipped if the MV5 API is unavailable or has a different signature.
     */
    private void tryMV5Import(String worldName) {
        try {
            Class<?> apiClass = Class.forName("org.mvplugins.multiverse.core.MultiverseCoreApi");
            Object api = apiClass.getMethod("get").invoke(null);
            Object wm = api.getClass().getMethod("getMvWorldManager").invoke(api);
            Class<?> optClass = Class.forName("org.mvplugins.multiverse.core.world.options.ImportWorldOptions");
            Object opts = optClass.getMethod("worldName", String.class).invoke(null, worldName);
            Object importResult = wm.getClass().getMethod("importWorld", optClass).invoke(wm, opts);
            LogUtil.log(host.getLogger(),
                    "MV5: imported '" + worldName + "' into MV registry: " + importResult, Level.INFO);
        } catch (ClassNotFoundException ignored) {
            // MV5 class path not found – silently skip
        } catch (Exception e) {
            LogUtil.log(host.getLogger(),
                    "MV5 import skipped (API mismatch or unsupported version): " + e.getMessage(), Level.FINE);
        }
    }

    /** Returns true if the world is currently loaded by the server. */
    public boolean isWorldLoaded(String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    /**
     * Returns all relevant worlds. MV4: returns only MV-managed worlds.
     * MV5 / standalone: returns all Bukkit worlds.
     */
    public List<World> getWorlds() {
        if (mvVersion == MVVersion.MV4) {
            try {
                Object wm = mvPlugin.getClass().getMethod("getMVWorldManager").invoke(mvPlugin);
                Collection<?> mvWorlds = (Collection<?>) wm.getClass().getMethod("getMVWorlds").invoke(wm);
                List<World> worlds = new ArrayList<>();
                for (Object mvWorld : mvWorlds) {
                    try {
                        World w = (World) mvWorld.getClass().getMethod("getCBWorld").invoke(mvWorld);
                        if (w != null) worlds.add(w);
                    } catch (Exception ignored) {}
                }
                if (!worlds.isEmpty()) return worlds;
            } catch (Exception e) {
                LogUtil.log(host.getLogger(),
                        "MV4 getWorlds error; falling back to Bukkit: " + e.getMessage(), Level.WARNING);
            }
        }
        return new ArrayList<>(Bukkit.getWorlds());
    }

    /** Looks up a public method by name and parameter types, returning null if not found. */
    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
