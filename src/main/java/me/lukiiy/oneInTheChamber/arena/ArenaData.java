package me.lukiiy.oneInTheChamber.arena;

import me.lukiiy.mapling.ManagedWorld;
import me.lukiiy.mapling.WorldData;
import me.lukiiy.mapling.provided.TomlWorldDataStore;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.util.List;

public class ArenaData {
    private final ManagedWorld<World> world;

    public ArenaData(ManagedWorld<World> world) {
        this.world = world;
    }

    public static ArenaData load(String name, File source, World world) { // just loads the data
        ManagedWorld<World> managed = new ManagedWorld<>(name, source, world, new WorldData());

        managed.setData(new TomlWorldDataStore().load(managed.getDataFile()));

        return new ArenaData(managed);
    }

    public World getWorld() {
        return world.getHandle();
    }

    public List<Location> getGroup(String group) {
        World w = getWorld();
        if (w == null) return List.of();

        return world.getData().group(group).stream().map(p -> new Location(w, p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch())).toList();
    }
}
