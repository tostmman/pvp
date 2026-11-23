package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Arena;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Fills / clears the same "prefight wall" regions PvPCore itself defines per arena
 * (Arena#wallMin/wallMax/finalWallMin/finalWallMax for "red"/"blue", built from
 * Arena#prefightWallMaterial()). This is the actual PvPCore "door": filled with
 * blocks during the countdown, cleared back to air the moment the fight starts.
 * If an arena has no wall region configured for a side, that side is simply skipped.
 */
final class ArenaDoors {
    private ArenaDoors() {}

    static void close(Arena arena) {
        Material mat = arena.prefightWallMaterial();
        fillSide("red", arena, mat != null ? mat : Material.BARRIER);
        fillSide("blue", arena, mat != null ? mat : Material.BARRIER);
    }

    static void open(Arena arena) {
        fillSide("red", arena, Material.AIR);
        fillSide("blue", arena, Material.AIR);
    }

    private static void fillSide(String side, Arena arena, Material mat) {
        if (arena.hasWall(side)) fillRegion(arena.wallMin(side), arena.wallMax(side), mat);
        if (arena.hasFinalWall(side)) fillRegion(arena.finalWallMin(side), arena.finalWallMax(side), mat);
    }

    private static void fillRegion(Location a, Location b, Material mat) {
        if (a == null || b == null) return;
        World w = a.getWorld();
        if (w == null) return;
        int x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX());
        int y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY());
        int z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ());
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    w.getBlockAt(x, y, z).setType(mat, false);
    }
}
