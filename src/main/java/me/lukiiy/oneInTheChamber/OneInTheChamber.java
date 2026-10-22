package me.lukiiy.oneInTheChamber;

import org.bukkit.plugin.java.JavaPlugin;

public final class OneInTheChamber extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static OneInTheChamber getInstance() {
        return JavaPlugin.getPlugin(OneInTheChamber.class);
    }
}
