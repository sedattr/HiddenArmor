package me.kteq.hiddenarmor.manager;

import me.kteq.hiddenarmor.HiddenArmor;
import me.kteq.hiddenarmor.handler.ArmorPacketHandler;
import me.kteq.hiddenarmor.handler.MessageHandler;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;

public class HiddenArmorManager {
    private Connection connection;
    private final HiddenArmor plugin;

    private Set<UUID> enabledPlayersUUID = new HashSet<>();
    private final Set<Predicate<Player>> forceDisablePredicates = new HashSet<>();
    private final Set<Predicate<Player>> forceEnablePredicates = new HashSet<>();

    private static String TABLE_NAME;
    private static String LINK;
    private static String URL;
    private static String USER;
    private static String PASSWORD;

    public boolean isConnected() {
        if (this.connection != null)
            try {
                return !this.connection.isClosed();
            } catch (Exception e) {
                e.printStackTrace();
            }

        return false;
    }

    public void shutdown() {
        try {
            if (isConnected())
                this.connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Connection getConnection() {
        try {
            if (isConnected())
                return this.connection;

            Class.forName(LINK);
            return this.connection = DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException | ClassNotFoundException throwable) {
            throwable.printStackTrace();
        }

        return null;
    }

    public HiddenArmorManager(HiddenArmor plugin) {
        this.plugin = plugin;
        registerDefaultPredicates();

        ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("mysql");
        if (section == null)
            return;

        TABLE_NAME = section.getString("table");
        LINK = section.getString("link");
        URL = section.getString("url");
        USER = section.getString("user");
        PASSWORD = section.getString("password");

        this.connection = getConnection();
        if (this.connection == null) {
            plugin.getLogger().log(Level.WARNING, "Could not enable connection");
            return;
        }
        try (
                Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (uuid VARCHAR(36) PRIMARY KEY);");
        ) {
            statement.execute();
        } catch (SQLException x) {
            x.printStackTrace();
        }

        loadEnabledPlayers();
    }

    public void togglePlayer(Player player, boolean inform) {
        if (isEnabled(player)) {
            disablePlayer(player, inform);
        } else {
            enablePlayer(player, inform);
        }
    }

    public void enablePlayer(Player player, boolean inform) {
        if (isEnabled(player)) return;
        if (inform) {
            Map<String, String> placeholderMap = new HashMap<>();
            placeholderMap.put("visibility", "%visibility-hidden%");
            MessageHandler.getInstance().message(ChatMessageType.ACTION_BAR, player, "%armor-visibility%", false, placeholderMap);
        }

        this.enabledPlayersUUID.add(player.getUniqueId());
        ArmorPacketHandler.getInstance().updatePlayer(player);

        savePlayer(player.getUniqueId());
    }

    public void disablePlayer(Player player, boolean inform) {
        if (!isEnabled(player))
            return;
        if (inform) {
            Map<String, String> placeholderMap = new HashMap<>();
            placeholderMap.put("visibility", "%visibility-shown%");
            MessageHandler.getInstance().message(ChatMessageType.ACTION_BAR, player, "%armor-visibility%", false, placeholderMap);
        }

        enabledPlayersUUID.remove(player.getUniqueId());
        ArmorPacketHandler.getInstance().updatePlayer(player);

        deletePlayer(player.getUniqueId());
    }

    public boolean isEnabled(Player player) {
        return this.enabledPlayersUUID.contains(player.getUniqueId());
    }

    public boolean isArmorHidden(Player player) {
        boolean hidden = isEnabled(player);
        for (Predicate<Player> predicate : forceDisablePredicates) {
            if (predicate.test(player)) {
                hidden = false;
                break;
            }
        }
        for (Predicate<Player> predicate : forceEnablePredicates) {
            if (predicate.test(player)) {
                hidden = true;
                break;
            }
        }
        return hidden;
    }

    private void registerDefaultPredicates() {
        boolean hideWhenInvisible = plugin.getConfig().getBoolean("invisibility-potion.always-hide-gear");
        forceDisablePredicates.add(player -> player.getGameMode().equals(GameMode.CREATIVE));
        forceDisablePredicates.add(player -> player.isInvisible() && !hideWhenInvisible);

        forceEnablePredicates.add(player -> player.isInvisible() && hideWhenInvisible);
    }

    private void deletePlayer(UUID uuid) {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE uuid = ?";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.execute();
            } catch (SQLException x) {
                plugin.getLogger().log(Level.WARNING, "Could not delete uuid " + uuid, x);
            }
        });
    }

    private void savePlayer(UUID uuid) {
        String sql = "REPLACE INTO " + TABLE_NAME + " (uuid) VALUES (?)";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (
                    PreparedStatement statement = getConnection().prepareStatement(sql)
            ) {
                statement.setString(1, uuid.toString());
                statement.execute();
            } catch (SQLException x) {
                plugin.getLogger().log(Level.WARNING, "Could not save uuid " + uuid, x);
            }
        });
    }

    // tabledeki butun uuidleri silmek icin herhangi bir kod yoktu sadece table sil geri olusturma vardi
    // bu da kotu fikir oldugu icin tek tek save/delete sistemine cevirdim
    // bu da hepsini saveleme ama drop statement hatali
    /*
    public void saveCurrentEnabledPlayers() {
        List<String> enabledUUIDs = this.enabledPlayersUUID.stream().map(UUID::toString).toList();

        String drop = "ALTER TABLE " + TABLE_NAME + " DROP COLUMN *";
        String add = "REPLACE INTO " + TABLE_NAME + " (uuid) VALUES (?)";

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (
                    PreparedStatement statement = getConnection().prepareStatement(drop)
            ) {
                statement.execute();
            } catch (SQLException x) {
                plugin.getLogger().log(Level.WARNING, "Could not drop columns", x);
            }

            for (String enabledUUID : enabledUUIDs) {
                try (
                        PreparedStatement statement = getConnection().prepareStatement(add)
                ) {
                    statement.setString(1, enabledUUID);

                    statement.execute();
                } catch (SQLException x) {
                    plugin.getLogger().log(Level.WARNING, "Could not save enabled players to database", x);
                }
            }
        });
    }
    */

    private void loadEnabledPlayers() {
        String sql = "SELECT * FROM " + TABLE_NAME;

        Set<UUID> uuid = new HashSet<>();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (
                    PreparedStatement statement = getConnection().prepareStatement(sql);
            ) {
                ResultSet set = statement.executeQuery();

                while (set.next()) {
                    uuid.add(UUID.fromString(set.getString("uuid")));
                }
            } catch (SQLException x) {
                plugin.getLogger().log(Level.WARNING, "Could not load enabled players from database", x);
            }
        });

        this.enabledPlayersUUID = uuid;
    }
}