/*
 * AntiCheat for Bukkit.
 * Copyright (C) 2012-2013 AntiCheat Team | http://gravitydevelopment.net
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

package net.h31ix.anticheat.util.enterprise;

import net.h31ix.anticheat.AntiCheat;
import net.h31ix.anticheat.manage.CheckType;
import net.h31ix.anticheat.manage.User;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;

public class Database {

    private static final String EVENTS_TABLE = "events";
    private static final String USERS_TABLE = "users";

    private String sqlLogEvent;
    private String sqlSyncTo;
    private String sqlSyncFrom;
    private String sqlCleanEvents;

    private String sqlCreateEvents;
    private String sqlCreateUsers;

    public enum DatabaseType {
        MySQL,
    }

    private DatabaseType type;
    private String hostname;
    private int port;
    private String username;
    private String password;
    private String prefix;
    private String schema;
    private String serverName;

    private int eventInterval;

    private int eventLife;

    private Connection connection;

    private PreparedStatement eventBatch;

    private BukkitTask eventTask;

    public Database(DatabaseType type, String hostname, int port, String username, String password, String prefix, String schema, String serverName, int eventInterval, int eventLife) {
        this.type = type;
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        this.prefix = prefix;
        this.schema = schema;
        this.serverName = serverName;

        this.eventInterval = eventInterval;

        this.eventLife = eventLife;

        sqlLogEvent = "INSERT INTO " + prefix + EVENTS_TABLE + " (server, user, check_type) VALUES (?, ?, ?)";
        sqlSyncTo = "INSERT INTO " + prefix + USERS_TABLE + " (user, level, last_update_server) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE level = ?, last_update=CURRENT_TIMESTAMP, last_update_server=?";
        sqlSyncFrom = "SELECT level FROM " + prefix + USERS_TABLE + " WHERE user = ?";
        sqlCleanEvents = "DELETE FROM " + prefix + EVENTS_TABLE + " WHERE time < (CURRENT_TIMESTAMP - INTERVAL ? DAY)";

        sqlCreateUsers = "CREATE TABLE IF NOT EXISTS " + prefix + USERS_TABLE + "(" +
                "  `user` VARCHAR(45) NOT NULL," +
                "  `level` INT NOT NULL," +
                "  `last_update` TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  `last_update_server` VARCHAR(45) NOT NULL," +
                "  PRIMARY KEY (`user`));";

        sqlCreateEvents = "CREATE TABLE IF NOT EXISTS " + prefix + EVENTS_TABLE + "(" +
                "  `id` INT NOT NULL AUTO_INCREMENT," +
                "  `server` VARCHAR(45) NOT NULL," +
                "  `time` TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  `user` VARCHAR(45) NOT NULL," +
                "  `check_type` VARCHAR(45) NOT NULL," +
                "  PRIMARY KEY (`id`));";
    }

    public DatabaseType getType() {
        return type;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSchema() {
        return schema;
    }

    public void connect() {
        String url = "jdbc:" + type.toString().toLowerCase() + "://" + hostname + ":" + port + "/" + schema;

        try {
            connection = DriverManager.getConnection(url, username, password);
            eventBatch = connection.prepareStatement(sqlLogEvent);

            connection.setAutoCommit(false);

            eventTask = Bukkit.getScheduler().runTaskTimerAsynchronously(AntiCheat.getPlugin(), new Runnable(){
                @Override
                public void run() {
                    flushEvents();
                }
            }, eventInterval* 60 * 20, eventInterval * 60 * 20);

        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            connection.prepareStatement(sqlCreateUsers).executeUpdate();
            connection.prepareStatement(sqlCreateEvents).executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        eventTask.cancel();

        flushEvents();

        try {
            eventBatch.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void logEvent(User user, CheckType checkType) {
        try {
            eventBatch.setString(1, serverName);
            eventBatch.setString(2, user.getName());
            eventBatch.setString(3, checkType.toString());

            eventBatch.addBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void syncTo(User user) {
        if (!user.isWaitingOnLevelSync()) {
            AntiCheat.debugLog("Syncing to "+user.getName()+" value "+user.getLevel());
            try {
                PreparedStatement statement = connection.prepareStatement(sqlSyncTo);
                statement.setString(1, user.getName());
                statement.setInt(2, user.getLevel());
                statement.setString(3, serverName);
                statement.setInt(4, user.getLevel());
                statement.setString(5, serverName);

                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void syncFrom(final User user) {
        Bukkit.getScheduler().runTaskAsynchronously(AntiCheat.getPlugin(), new Runnable(){
            @Override
            public void run() {
                try {
                    PreparedStatement statement = connection.prepareStatement(sqlSyncFrom);
                    statement.setString(1, user.getName());

                    ResultSet set = statement.executeQuery();
                    while (set.next()) {
                        AntiCheat.debugLog("Syncing from "+user.getName()+" value "+set.getInt("level"));
                        user.setLevel(set.getInt("level"));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void flushEvents() {
        try {
            eventBatch.executeBatch();
            connection.commit();

            eventBatch = connection.prepareStatement(sqlLogEvent);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void cleanEvents() {
        Bukkit.getScheduler().runTaskAsynchronously(AntiCheat.getPlugin(), new Runnable(){
            @Override
            public void run() {
                try {
                    PreparedStatement statement = connection.prepareStatement(sqlCleanEvents);
                    statement.setInt(1, eventLife);

                    statement.executeUpdate();

                    connection.commit();
                    AntiCheat.getPlugin().verboseLog("Cleaned " + statement.getUpdateCount() + " old events from the database");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}