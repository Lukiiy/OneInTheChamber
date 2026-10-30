package me.lukiiy.oneInTheChamber;

import me.lukiiy.flow.*;
import me.lukiiy.oneInTheChamber.arena.ArenaAdapter;
import me.lukiiy.oneInTheChamber.arena.ArenaData;
import me.lukiiy.wayTrick.LocatorBar;
import me.lukiiy.wayTrick.Waypoint;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Game extends Minigame {
    private ArenaData arena;
    private World world;
    private List<Location> spawns;
    private LocatorBar locatorBar;

    private int tickCount = 0;
    private final AtomicInteger timer = new AtomicInteger(0);
    public final AtomicBoolean endSequence = new AtomicBoolean(false);
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();
    private int scoreLimit;
    public boolean going = false;

    private final AttributeModifier attrMod = new AttributeModifier(new NamespacedKey(OneInTheChamber.getInstance(), "a"), -6e7, AttributeModifier.Operation.ADD_NUMBER);

    private final GameHandler timerSystem = _ -> {
        if (++tickCount % 20 != 0) return;

        updateBoards();

        if (timer.decrementAndGet() <= 0) {
            end(getPlayers().stream().filter(FlowPlayer.class::isInstance).map(FlowPlayer.class::cast).max(Comparator.comparingInt(fp -> Optional.ofNullable(fp.get(Stats.class)).map(Stats::getScore).orElse(0))).orElse(null));

            return;
        }

        refreshBar();
    };

    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

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
        world.setGameRule(GameRules.LOCATOR_BAR, true);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);

        spawns = new ArrayList<>(arena.getGroup("spawn").stream().map(spawn -> {
            Vector dir = world.getSpawnLocation().toVector().subtract(spawn.toVector());
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));

            return new Location(spawn.getWorld(), spawn.getX(), spawn.getY(), spawn.getZ(), yaw, 0f);
        }).toList());
    }

    @Override
    protected void onStart() {
        timer.set(entry().timer.getValue().intValue() * 60);
        scoreLimit = entry().score.getValue().intValue();
        locatorBar = new LocatorBar(OneInTheChamber.getInstance());
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
            if (entry().doubleJump.getValue()) p.setAllowFlight(true);

            AttributeInstance transmit = p.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
            if (transmit != null) transmit.addModifier(attrMod);
        });

        refreshBar();

        int totalSeconds = 5;
        new Countdown(OneInTheChamber.getInstance(), Duration.ofSeconds(totalSeconds), c -> {
            int secondsLeft = (int) (c + 1);

            TextColor color;
            if (secondsLeft > 3) color = FDefaults.GREEN;
            else if (secondsLeft == 2) color = FDefaults.ORANGE;
            else if (secondsLeft == 1) color = FDefaults.RED;
            else color = FDefaults.YELLOW;

            // Action bar countdown :3
            Component activeC = Component.text("▌".repeat(secondsLeft)).color(color);
            Component inactiveC = Component.text("▌".repeat(totalSeconds - secondsLeft)).color(FDefaults.DARK_GRAY);
            Component secC = Component.text(" " + secondsLeft);

            forEachPlayer(p -> {
                Player player = p.getPlayer();

                player.sendActionBar(Component.text("Game Start ").append(Component.text("»").decorate(TextDecoration.BOLD)).appendSpace().append(activeC).append(inactiveC).append(secC));
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, 1f + ((totalSeconds - secondsLeft) * .15f));
            });
        }, () -> {
            going = true;

            addSystem(timerSystem);
            getActualPlayers().forEach(target -> {
                Waypoint waypoint = locatorBar.track(target).color(Color.WHITE);

                getActualPlayers().stream().filter(viewer -> !viewer.getUniqueId().equals(target.getUniqueId())).forEach(waypoint::showTo);
            });
        }).start();
    }

    @Override
    protected void onStop() {
        boards.values().forEach(Board::destroy);
        forEachPlayer(fp -> {
            Player p = fp.getPlayer();

            p.hideBossBar(bossBar);

            AttributeInstance transmit = p.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
            if (transmit != null) transmit.removeModifier(attrMod);
        });

        BaseLobby lobby = Flow.getInstance().getManager().getLobby();
        if (lobby != null) forEachPlayer(lobby::sendToLobby);

        if (arena != null) {
            OneInTheChamber.getInstance().adapter.unload(world);

            arena = null;
        }

        boards.clear();

        if (locatorBar != null) {
            locatorBar.close();
            locatorBar = null;
        }
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
        bossBar.name(Component.text("Reach " + scoreLimit + "! ◆ " + timer.intValue() + "s left").color(FDefaults.PURPLE));
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