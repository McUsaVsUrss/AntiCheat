/*
 * AntiCheat for Bukkit.
 * Copyright (C) 2012-2014 AntiCheat Team | http://gravitydevelopment.net
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package net.gravitydevelopment.anticheat;

import com.comphenix.protocol.ProtocolLibrary;
import net.gravitydevelopment.anticheat.command.CommandHandler;
import net.gravitydevelopment.anticheat.config.Configuration;
import net.gravitydevelopment.anticheat.event.*;
import net.gravitydevelopment.anticheat.manage.AntiCheatManager;
import net.gravitydevelopment.anticheat.manage.PacketManager;
import net.gravitydevelopment.anticheat.util.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class AntiCheat extends JavaPlugin {

    private static final int PROJECT_ID = 38723;
    private static AntiCheatManager manager;
    private static AntiCheat plugin;
    private static List<Listener> eventList = new ArrayList<Listener>();
    private static boolean update = false;
    private static String updateDetails = null;
    private static Configuration config;
    private static boolean verbose;
    private static boolean developer;
    private static PacketManager packetManager;
    private static boolean protocolLib = false;
    private static Long loadTime;

    public static AntiCheat getPlugin() {
        return plugin;
    }

    public static AntiCheatManager getManager() {
        return manager;
    }

    public static boolean isUpdated() {
        return !update;
    }

    public static String getUpdateDetails() {
        return updateDetails;
    }

    public static String getVersion() {
        return manager.getPlugin().getDescription().getVersion();
    }

    public static boolean developerMode() {
        return developer;
    }

    public static void setDeveloperMode(boolean b) {
        developer = b;
    }

    public static boolean isUsingProtocolLib() {
        return protocolLib;
    }

    public static void debugLog(final String string) {
        Bukkit.getScheduler().runTask(getPlugin(), new Runnable() {
            public void run() {
                if (developer) {
                    manager.debugLog("[DEBUG] " + string);
                }
            }
        });
    }

    @Override
    public void onEnable() {
        plugin = this;
        loadTime = System.currentTimeMillis();
        manager = new AntiCheatManager(this, getLogger());
        eventList.add(new PlayerListener());
        eventList.add(new BlockListener());
        eventList.add(new EntityListener());
        eventList.add(new VehicleListener());
        eventList.add(new InventoryListener());
        // Order is important in some cases, don't screw with these unless needed, especially config

        setupConfig();
        setupEvents();
        setupCommands();
        // setupProtocol(); TODO
        // Enterprise must come before levels
        setupEnterprise();
        restoreLevels();
        // Check if NoCheatPlus is installed
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            public void run() {
                if (Bukkit.getPluginManager().getPlugin("NoCheatPlus") != null) {
                    getLogger().severe("You are also running NoCheatPlus!");
                    getLogger().severe("NoCheatPlus has been known to conflict with AntiCheat's results and create false cheat detections.");
                    getLogger().severe("Please remove or disable NoCheatPlus to silence this warning.");
                }
            }
        }, 40L);
        // End tests
        verboseLog("Finished loading.");
    }

    @Override
    public void onDisable() {
        verboseLog("Saving user levels...");
        config.getLevels().saveLevelsFromUsers(getManager().getUserManager().getUsers());

        AntiCheatManager.close();
        getServer().getScheduler().cancelTasks(this);
        cleanup();
    }

    private void setupProtocol() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            protocolLib = true;
            packetManager = new PacketManager(ProtocolLibrary.getProtocolManager(), this, manager);
            verboseLog("Hooked into ProtocolLib");
        }
    }

    private void setupEvents() {
        for (Listener listener : eventList) {
            getServer().getPluginManager().registerEvents(listener, this);
            verboseLog("Registered events for ".concat(listener.toString().split("@")[0].split(".anticheat.")[1]));
        }
    }

    private void setupCommands() {
        getCommand("anticheat").setExecutor(new CommandHandler());
        verboseLog("Registered commands.");
    }

    private void setupConfig() {
        config = manager.getConfiguration();
        verboseLog("Setup the config.");
    }

    private void setupEnterprise() {
        if (config.getConfig().enterprise.getValue()) {
            if (config.getEnterprise().loggingEnabled.getValue()) {
                config.getEnterprise().database.cleanEvents();
            }
        }
    }

    private void restoreLevels() {
        for (Player player : getServer().getOnlinePlayers()) {
            String name = player.getName();

            User user = new User(name);
            user.setIsWaitingOnLevelSync(true);
            config.getLevels().loadLevelToUser(user);

            manager.getUserManager().addUser(user);
            verboseLog("Data for " + name + " loaded");
        }
    }

    private void cleanup() {
        eventList = null;
        manager = null;
        config = null;
    }

    public void verboseLog(final String string) {
        if (verbose) {
            getLogger().info(string);
        }
    }

    public void setVerbose(boolean b) {
        verbose = b;
    }

    public Long getLoadTime() {
        return loadTime;
    }
}
