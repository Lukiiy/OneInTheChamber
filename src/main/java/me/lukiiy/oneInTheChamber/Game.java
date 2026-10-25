package me.lukiiy.oneInTheChamber;

import me.lukiiy.flow.*;
import me.lukiiy.oneInTheChamber.arena.ArenaAdapter;
import me.lukiiy.oneInTheChamber.arena.ArenaData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Game extends Minigame {
    private ArenaData arena;
    private World world;
    private List<Location> spawns;

    private int tickCount = 0;
    private final AtomicInteger timer = new AtomicInteger(0);
    public final AtomicBoolean endSequence = new AtomicBoolean(false);
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();
    private int scoreLimit;

    private final GameHandler timerSystem = _ -> {
        if (++tickCount % 20 != 0) return;

        updateBoards();

        if (timer.decrementAndGet() <= 0) {
            end(getPlayers().stream().filter(FlowPlayer.class::isInstance).map(FlowPlayer.class::cast).max(Comparator.comparingInt(fp -> Optional.ofNullable(fp.get(Stats.class)).map(Stats::getScore).orElse(0))).orElse(null));

            return;
        }

        refreshBar();
    };

    private final BossBar bossBar = BossBar.bossBar(Component.text("Waiting...").color(NamedTextColor.GOLD), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    @Override
    protected List<Listener> listeners() {
        return List.of(new Listen(this));
    }

    @Override
    protected void prepare() {
        String map = entry().map.getValue();
        File source = new File(ArenaAdapter.MAPS_DIR, map);
        if (!source.isDirectory() || (world = OneInTheChamber.getInstance().adapter.load(source)) == null) throw new MinigameException("No arena is selected or registered."); // this feels like cheating lol

        arena = ArenaData.load(map, source, world);

        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);

        spawns = new ArrayList<>(arena.getGroup("spawn"));
    }

    @Override
    protected void onStart() {
        timer.set(entry().timer.getValue().intValue() * 60);
        scoreLimit = entry().score.getValue().intValue();
        Collections.shuffle(spawns);

        Iterator<Location> spawnIt = spawns.iterator();

        forEachPlayer(fp -> {
            fp.attach(new Stats());

            Player p = fp.getPlayer();
            Location location = spawnIt.hasNext() ? spawnIt.next() : spawns.get(random.nextInt(spawns.size()));

            FUtils.softReset(p, GameMode.ADVENTURE);
            p.showBossBar(bossBar);
            p.teleport(spawnIt.hasNext() ? spawnIt.next() : location);
            p.setRespawnLocation(spawns.get(random.nextInt(spawns.size())), true);
            Items.apply(p);
            boards.put(p.getUniqueId(), new Board(p));
        });

        refreshBar();
        addSystem(timerSystem);
    }

    @Override
    protected void onStop() {
        boards.values().forEach(Board::destroy);
        forEachPlayer(fp -> fp.getPlayer().hideBossBar(bossBar));

        BaseLobby lobby = Flow.getInstance().getManager().getLobby();
        if (lobby != null) forEachPlayer(lobby::sendToLobby);

        if (arena != null) {
            OneInTheChamber.getInstance().adapter.unload(world);

            arena = null;
        }

        boards.clear();
    }

    // applied after someone respawns
    public void respawnStuff(Player p) {
        Collections.shuffle(spawns);
        p.setRespawnLocation(spawns.get(random.nextInt(spawns.size())), true);
        Items.apply(p);
    }

    public void addScore(FlowPlayer player) {
        Stats stats = player.get(Stats.class);
        if (stats == null) return;

        stats.addScore(1);
        if (stats.getScore() >= scoreLimit) end(player);

        updateBoards();
    }

    private void refreshBar() {
        bossBar.name(Component.text("Reach " + scoreLimit + " points ▌ " + timer.intValue() + "s left").color(FDefaults.PURPLE));
    }

    private void end(FlowPlayer winner) {
        if (endSequence.get()) return;

        removeSystem(timerSystem);
        endSequence.set(true);

        List<Player> viewers = new ArrayList<>();

        bossBar.viewers().forEach(viewer -> {
            if (viewer instanceof Player p) viewers.add(p);
        });

        viewers.forEach(bossBar::removeViewer);

        Bukkit.getGlobalRegionScheduler().run(OneInTheChamber.getInstance(), _ -> {
            if (winner != null) {
                broadcast(Component.newline().append(Component.text("  ☄ ").color(TextColor.color(0x9DE664))).append(winner.getPlayer().displayName().color(FDefaults.LIME)).append(Component.text(" has won!").color(FDefaults.GREEN)).appendNewline());
                forEachPlayer(fp -> fp.getPlayer().playSound(fp.getPlayer(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f));
            } else {
                broadcast(Component.newline().append(Component.text("  ✘ ").color(FDefaults.DARK_GRAY)).append(Component.text("Nobody won.").color(FDefaults.GRAY)).appendNewline());
            }
        });

        Bukkit.getGlobalRegionScheduler().runDelayed(OneInTheChamber.getInstance(), _ -> stop(), 100L);
    }

    public class Board {
        private final OnlyBoard board;
        private final Player player;

        public Board(Player player) {
            this.player = player;
            this.board = new OnlyBoard(player, Component.text("OITC").color(FDefaults.YELLOW).decorate(TextDecoration.BOLD));
        }

        public void update() {
            var scores = getPlayers().stream().filter(FlowPlayer.class::isInstance).map(FlowPlayer.class::cast).map(fp -> Map.entry(fp.getPlayer(), Optional.ofNullable(fp.get(Stats.class)).map(Stats::getScore).orElse(0))).sorted(Map.Entry.<Player, Integer>comparingByValue().reversed()).toList();

            int playerIdx = -1;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.empty());

            for (int i = 0; i < scores.size(); i++) {
                if (scores.get(i).getKey().equals(player)) {
                    playerIdx = i;

                    break;
                }
            }

            int limit = Math.min(10, scores.size());
            for (int i = 0; i < limit; i++) {
                Map.Entry<Player, Integer> entry = scores.get(i);

                lines.add(format(i + 1, entry.getKey().getName(), entry.getValue(), entry.getKey().equals(player)));
            }

            if (playerIdx >= 10) {
                Map.Entry<Player, Integer> entry = scores.get(playerIdx);

                lines.add(Component.empty());
                lines.add(format(playerIdx + 1, entry.getKey().getName(), entry.getValue(), true));
            }

            lines.add(Component.empty());
            board.lines(lines.toArray(Component[]::new));
        }

        private Component format(int rank, String name, int score, boolean highlight) {
            return Component.text(rank + ". " + name + ": ").color(highlight ? FDefaults.LIGHT_YELLOW : FDefaults.WHITE).append(Component.text(score).color(highlight ? FDefaults.YELLOW : FDefaults.GRAY));
        }

        public void destroy() {
            board.destroy();
        }
    }

    private void updateBoards() {
        boards.values().forEach(Board::update);
    }

    public Entry entry() {
        return (Entry) entry;
    }
}