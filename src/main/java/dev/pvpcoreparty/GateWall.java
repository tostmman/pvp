package dev.pvpcoreparty;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * A visual "gate" wall made of BlockDisplay entities, placed in front of a spawn point during the
 * countdown, that rises up and disappears the instant the fight starts.
 *
 * PvPCore has its own, far more elaborate gate (prefight.PreFightSkipSequence): animated fence-gate
 * panels players can hit to ready up early, built on packetevents and tied directly to PvPCore's own
 * DuelMatch class. I could not reuse that class as-is - no source code is available (only compiled
 * .class files), its constructor requires a PvPCore DuelMatch specifically, and it needs packetevents.
 * This is a from-scratch stand-in using only vanilla Paper entities: same idea (a wall that seals the
 * spawn, then opens the instant the fight starts), simpler animation, no "hit to ready up early" part.
 * It is purely cosmetic - players can't actually walk through it anyway because the party-plugin's
 * movement lock already keeps everyone pinned to their spawn during the countdown.
 */
public final class GateWall {
    private final List<BlockDisplay> pieces = new ArrayList<>();
    private boolean opened;

    public GateWall(Location spawn, Material material, int width, int height) {
        if (spawn == null || spawn.getWorld() == null) return;
        Vector dir = spawn.getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX());
        Location base = spawn.clone().add(dir.clone().multiply(1.5));
        BlockData data = material.createBlockData();
        for (int row = 0; row < height; row++) {
            for (int col = -(width / 2); col <= width / 2; col++) {
                Location loc = base.clone().add(right.clone().multiply(col)).add(0, row, 0);
                Location blockLoc = new Location(loc.getWorld(), Math.floor(loc.getX()), Math.floor(loc.getY()), Math.floor(loc.getZ()));
                BlockDisplay bd = spawn.getWorld().spawn(blockLoc, BlockDisplay.class, e -> {
                    e.setBlock(data);
                    e.setPersistent(false);
                    e.setGravity(false);
                    e.setBrightness(new Display.Brightness(15, 15));
                });
                pieces.add(bd);
            }
        }
    }

    /** Lifts every piece upward and removes it a moment later - the "gate opens" moment. */
    public void open(Plugin plugin) {
        if (opened) return;
        opened = true;
        for (BlockDisplay bd : pieces) {
            if (bd == null || bd.isDead()) continue;
            bd.getWorld().playSound(bd.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 0.8f);
            Location start = bd.getLocation();
            new BukkitRunnable() {
                int tick = 0;
                @Override public void run() {
                    tick++;
                    if (bd.isDead() || tick > 10) { if (!bd.isDead()) bd.remove(); cancel(); return; }
                    bd.teleport(start.clone().add(0, 0.3 * tick, 0));
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    /** Removes every piece immediately with no animation (used when a match is force-ended early). */
    public void removeInstant() {
        opened = true;
        for (BlockDisplay bd : pieces) if (bd != null && !bd.isDead()) bd.remove();
    }
}
