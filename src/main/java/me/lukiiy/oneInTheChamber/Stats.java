package me.lukiiy.oneInTheChamber;

import me.lukiiy.flow.component.GameComponent;
import org.bukkit.entity.Player;

public class Stats implements GameComponent {
    private int score = 0;
    public Player last;

    public int getScore() {
        return score;
    }

    public void addScore(int amount) {
        this.score += amount;
    }
}
