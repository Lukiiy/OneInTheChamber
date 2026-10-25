package me.lukiiy.oneInTheChamber;

import me.lukiiy.flow.GameEntry;
import me.lukiiy.flow.setting.BooleanSetting;
import me.lukiiy.flow.setting.CycleSetting;
import me.lukiiy.flow.setting.DoubleSetting;
import me.lukiiy.flow.setting.Option;
import me.lukiiy.oneInTheChamber.arena.ArenaAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public class Entry extends GameEntry {
    public final CycleSetting<String> map = setting(new CycleSetting<>("map", "Arena", "The arena the game will run on!", () -> {
        List<Option<String>> maps = OneInTheChamber.getInstance().adapter.listAvailableMaps().stream().map(name -> new Option<>(name, ArenaAdapter.formatString(name))).toList();

        return maps.isEmpty() ? List.of(new Option<>("none", "No maps available")) : maps;
    }));

    public final DoubleSetting timer = setting(new DoubleSetting("timer", "Timer (minutes)", "", 1, 30, 5, 1));
    public final DoubleSetting score = setting(new DoubleSetting("score", "Max Score", "Score needed to win the game", 5, 100, 20, 1));
    public final BooleanSetting doubleJump = setting(new BooleanSetting("doubleJump", "Double Jump", "Players spawn next to each other", false));

    public Entry() {
        super("oitc", "One In The Chamber", Game::new, Component.text("One in the Chamber").color(NamedTextColor.GOLD));
    }
}
