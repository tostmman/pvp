package dev.pvpcoreparty;

import dev.rean.pvpcore.duel.DuelMatch;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.*;

/**
 * Server-wide match rules, for PvPCore's 1v1 duels AND the party team battles:
 *  - during the countdown, between rounds and after the match is won, players can't use/throw/drop/place anything
 *  - ender pearls only live while their owner is in a fighting match; they vanish when the owner quits,
 *    when the round/match ends, and on server restart.
 */
public final class MatchGuard implements Listener {
    private final PvPCoreParty plugin;
    private final Map<UUID, UUID> pearls = new HashMap<>(); // pearl entity -> owner

    public MatchGuard(PvPCoreParty plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L);
        // pearls that survived a restart (they are saved with the world) are wiped right after startup
        Bukkit.getScheduler().runTask(plugin, this::removeAllPlayerPearls);
    }

    // ---------- state helpers ----------

    public boolean inAnyMatch(UUID id) {
        return plugin.matches().inMatch(id) || plugin.core().duelManager().isInMatch(id);
    }

    /** In a match that is NOT in its fighting phase (countdown, between rounds, ended). */
    public boolean blocked(UUID id) {
        PartyMatch tm = plugin.matches().get(id);
        if (tm != null) return tm.state() != PartyMatch.State.FIGHTING;
        DuelMatch dm = plugin.core().duelManager().getMatch(id);
        return dm != null && !dm.isFighting();
    }

    // ---------- no using / throwing / dropping / placing while blocked ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (blocked(e.getPlayer().getUniqueId())) e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && blocked(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackPearl(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p && inAnyMatch(p.getUniqueId())) {
            pearls.put(pearl.getUniqueId(), p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBow(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && blocked(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if ((c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT)
                && blocked(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // ---------- pearl cleanup ----------

    /** Player quits: any pearl they threw disappears instantly (PvPCore / the team match end the duel itself). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        removePearlsOf(e.getPlayer().getUniqueId());
    }

    /** Pearls loaded back from disk (e.g. after a restart) are removed. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        List<Entity> stale = new ArrayList<>();
        for (Entity ent : e.getEntities()) {
            if (ent instanceof EnderPearl pearl && pearl.getShooter() instanceof Player) stale.add(ent);
        }
        if (stale.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> stale.forEach(Entity::remove));
    }

    private void tick() {
        if (pearls.isEmpty()) return;
        Iterator<Map.Entry<UUID, UUID>> it = pearls.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (ent == null || !ent.isValid()) { it.remove(); continue; }
            UUID owner = en.getValue();
            if (Bukkit.getPlayer(owner) == null || !inAnyMatch(owner) || blocked(owner)) {
                ent.remove();
                it.remove();
            }
        }
    }

    public void removePearlsOf(UUID id) {
        for (World w : Bukkit.getWorlds()) {
            for (EnderPearl pearl : w.getEntitiesByClass(EnderPearl.class)) {
                if (pearl.getShooter() instanceof Player sp && sp.getUniqueId().equals(id)) pearl.remove();
            }
        }
        pearls.values().removeIf(id::equals);
    }

    public void removeAllPlayerPearls() {
        for (World w : Bukkit.getWorlds()) {
            for (EnderPearl pearl : w.getEntitiesByClass(EnderPearl.class)) {
                if (pearl.getShooter() instanceof Player) pearl.remove();
            }
        }
        pearls.clear();
    }

    /** Server stopping / plugin disabling: no pearl may survive into the next start. */
    public void shutdown() {
        removeAllPlayerPearls();
    }
}
