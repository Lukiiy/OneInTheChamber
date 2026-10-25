package me.lukiiy.oneInTheChamber;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import me.lukiiy.flow.FDefaults;
import me.lukiiy.flow.FlowPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Listen implements Listener {
    private final Game game;
    private final Set<UUID> poison = new HashSet<>();

    public Listen(Game game) {
        this.game = game;
    }

    private FlowPlayer getFlowPlayer(Player player) {
        return game.getPlayers().stream().filter(p -> p instanceof FlowPlayer fp && fp.getPlayer().getUniqueId().equals(player.getUniqueId())).map(p -> (FlowPlayer) p).findFirst().orElse(null);
    }

    @EventHandler
    public void dmg(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            return;
        }

        FlowPlayer fp = getFlowPlayer(victim);
        if (fp == null) return;

        if (e instanceof EntityDamageByEntityEvent byEntity) {
            Stats stats = fp.get(Stats.class);
            if (stats == null) return;

            if (byEntity.getDamager() instanceof Player a) stats.last = a;
            if (byEntity.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player shooter) {
                e.setDamage(victim.getHealth());

                stats.last = shooter;
            }
        }
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        Player p = e.getEntity();
        FlowPlayer fp = getFlowPlayer(p);
        if (fp == null) return;

        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.deathScreenMessageOverride(null);

        Stats stats = fp.get(Stats.class);
        if (stats == null || stats.last == null) return;

        Player last = stats.last;
        FlowPlayer lastFp = getFlowPlayer(last);
        if (lastFp == null) return;

        last.getInventory().addItem(Items.ARROW);
        last.heal(20, EntityRegainHealthEvent.RegainReason.SATIATED);
        last.playSound(last, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.8f);
        game.addScore(lastFp);

        String way = e.getDamageSource().isIndirect() ? "\uD83C\uDFF9" : "\uD83D\uDDE1";

        e.deathMessage(Component.empty().append(Component.text("☠ ").color(FDefaults.RED)).append(last.displayName().color(FDefaults.LIGHT_YELLOW)).append(Component.text(" " + way + " ").color(FDefaults.YELLOW)).append(p.displayName().color(FDefaults.LIGHT_YELLOW)));
    }

    @EventHandler
    public void durability(PlayerItemDamageEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (poison.contains(p.getUniqueId()) || p.getGameMode().isInvulnerable() || getFlowPlayer(p) == null) return;

        if (e.getTo().getBlock().getType() == Material.WATER) {
            poison.add(p.getUniqueId());

            DamageSource source = DamageSource.builder(DamageType.DROWN).build();

            p.getScheduler().runAtFixedRate(OneInTheChamber.getInstance(), (t) -> {
                if (!p.getLocation().getBlock().isLiquid() || !p.isOnline()) {
                    t.cancel();
                    poison.remove(p.getUniqueId());
                }

                p.damage(2, source);
            }, null, 5L, 20L);
        }
    }

    @EventHandler
    public void arrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;

        arrow.remove();
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void respawn(PlayerPostRespawnEvent e) {
        if (getFlowPlayer(e.getPlayer()) == null) return;

        game.respawnStuff(e.getPlayer());
    }

    @EventHandler
    public void drop(PlayerDropItemEvent e) {
        if (getFlowPlayer(e.getPlayer()) == null) return;

        e.setCancelled(true);
    }
}