package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Arena;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Closes/opens PvPCore's own "prefight wall" regions for an arena (the same red/blue wall regions and
 * material configured in PvPCore itself via /arena setwall) before/after the countdown. The actual
 * filling logic is private inside PvPCore's compiled DuelMatch class (no source, so it can't be called
 * directly) - this rebuilds it from the data Arena itself exposes (wallMin/wallMax/hasWall/prefightWallMaterial),
 * so it uses the exact same region & block type the server already has configured for duels.
 * If an arena has no wall region configured for a side, that side is simply skipped (nothing to close).
 */
public final class ArenaDoor {
    private ArenaDoor() {}

    private static final String[] SIDES = { "red", "blue" };

    public static void close(Arena arena) {
        Material mat = arena.prefightWallMaterial() != null ? arena.prefightWallMaterial() : Material.BARRIER;
        for (String side : SIDES) fill(arena, side, mat);
    }

    public static void open(Arena arena) {
        for (String side : SIDES) fill(arena, side, Material.AIR);
    }

    private static void fill(Arena arena, String side, Material mat) {
        if (!arena.hasWall(side)) return;
        Location a = arena.wallMin(side);
        Location b = arena.wallMax(side);
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
