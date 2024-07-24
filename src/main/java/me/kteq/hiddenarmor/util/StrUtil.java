package me.kteq.hiddenarmor.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public abstract class StrUtil {
    public static String color(String s){
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
