package me.lukiiy.oneInTheChamber;

import me.lukiiy.flow.Flow;
import me.lukiiy.oneInTheChamber.arena.ArenaAdapter;
import org.bukkit.plugin.java.JavaPlugin;

public final class OneInTheChamber extends JavaPlugin {
    public final ArenaAdapter adapter = new ArenaAdapter();

    @Override
    public void onEnable() {
        Flow.getInstance().getManager().register(new Entry());
    }

    public static OneInTheChamber getInstance() {
        return JavaPlugin.getPlugin(OneInTheChamber.class);
    }
}
