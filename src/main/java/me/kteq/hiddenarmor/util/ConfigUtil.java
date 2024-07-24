package me.kteq.hiddenarmor.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigUtil {
    public static FileConfiguration getYamlConfiguration(File customConfigFile) {
        if (!customConfigFile.exists())
            return null;

        return YamlConfiguration.loadConfiguration(customConfigFile);
    }
}