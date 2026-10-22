package me.lukiiy.oneInTheChamber;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;

// A very small hardcoded in-lib to do the scoreboard -- taken from FortunePillars
public class OnlyBoard {
    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Map<Integer, Team> teams = new HashMap<>();

    public OnlyBoard(Player player, Component title) {
        this.player = player;
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective("oitc_sb", Criteria.DUMMY, title);

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        this.player.setScoreboard(scoreboard);
    }

    public void setTitle(Component title) {
        objective.displayName(title);
    }

    public void updateLines(Component... lines) {
        int total = lines.length;

        for (int i = 0; i < total; i++) {
            Component component = lines[i];
            int scoreIdx = total - i;

            Team team = teams.computeIfAbsent(scoreIdx, idx -> {
                String anchor = "§" + Integer.toHexString(idx);
                Team newTeam = scoreboard.registerNewTeam("line_" + idx);

                newTeam.addEntry(anchor);
                objective.getScore(anchor).setScore(idx);

                return newTeam;
            });

            team.prefix(component);
        }

        var iterator = teams.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            Team team = entry.getValue();

            if (entry.getKey() > total) {
                for (String tEntry : team.getEntries()) scoreboard.resetScores(tEntry);

                team.unregister();
                iterator.remove();
            }
        }
    }

    public void destroy() {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}