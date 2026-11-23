package dev.pvpcoreparty;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;
import dev.rean.pvpcore.gui.GuiContext;
import dev.rean.pvpcore.gui.ReanMenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PartyListener implements Listener {
    private final PvPCoreParty plugin;

    public PartyListener(PvPCoreParty plugin) { this.plugin = plugin; }

    private PartyMatch match(Player p) { return plugin.matches().get(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PartyMatch m = match(p);
        if (m == null) return;
        if (m.state() != PartyMatch.State.FIGHTING || !m.isAlive(p.getUniqueId())) { e.setCancelled(true); return; }
        if (e instanceof EntityDamageByEntityEvent de) {
            Player damager = null;
            if (de.getDamager() instanceof Player dp) damager = dp;
            else if (de.getDamager() instanceof org.bukkit.entity.Projectile proj) {
                ProjectileSource src = proj.getShooter();
                if (src instanceof Player sp) damager = sp;
            }
            if (damager != null && !damager.equals(p) && m.sameTeam(damager.getUniqueId(), p.getUniqueId())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PartyMatch m = match(p);
        if (m == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepInventory(false);
        e.deathMessage(null);
        m.handleDeath(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PartyMatch m = match(p);
        if (m == null) return;
        Location loc = m.spectatorSpawn();
        if (loc != null) e.setRespawnLocation(loc);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) m.makeSpectator(p); }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.parties().forget(e.getPlayer().getUniqueId());
        plugin.parties().removeMember(e.getPlayer().getUniqueId(), "disconnected");
        plugin.matches().handleLeave(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        PartyMatch m = match(e.getPlayer());
        if (m == null || m.state() != PartyMatch.State.COUNTDOWN) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            e.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        PartyMatch m = match(e.getPlayer());
        if (m == null) return;
        if (!m.kit().canBreakBlock(e.getBlock().getType())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PartyMatch m = match(p);
        if (m == null || m.kit().naturalRegen()) return;
        if (e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || e.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQueueCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.parties().get(p.getUniqueId()) == null) return;
        String[] parts = e.getMessage().substring(1).split(" ");
        if (!parts[0].equalsIgnoreCase("queue")) return;
        if (parts.length > 1 && (parts[1].equalsIgnoreCase("leave") || parts[1].equalsIgnoreCase("leaveall"))) return;
        e.setCancelled(true);
        plugin.send(p, "&cYou can't queue while in a party. Leave your party first.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (match(e.getPlayer()) == null || plugin.core().settings().allowCommandsInMatch()) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (cmd.equals("party") || cmd.equals("p") || cmd.equals("pc") || cmd.equals("partychat")) return;
        e.setCancelled(true);
        plugin.send(e.getPlayer(), "&cYou can't use commands during a team battle.");
    }

    // ---------- party horn (slot 2) ----------

    private final Map<UUID, Long> hornCooldown = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHorn(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.items().isPartyItem(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (now - hornCooldown.getOrDefault(p.getUniqueId(), 0L) < 300) return;
        hornCooldown.put(p.getUniqueId(), now);
        plugin.menus().openMain(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHornDrop(PlayerDropItemEvent e) {
        if (plugin.items().isPartyItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHornSwap(PlayerSwapHandItemEvent e) {
        if (plugin.items().isPartyItem(e.getMainHandItem()) || plugin.items().isPartyItem(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // menu clicks
        if (e.getView().getTopInventory().getHolder() instanceof PartyMenus.Holder h) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            var action = h.action(e.getRawSlot());
            if (action != null) action.accept(p, e.getClick());
            return;
        }
        // keep the horn from being moved around
        boolean hornInvolved = plugin.items().isPartyItem(e.getCurrentItem()) || plugin.items().isPartyItem(e.getCursor());
        if (!hornInvolved && e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
            hornInvolved = plugin.items().isPartyItem(p.getInventory().getItem(e.getHotbarButton()));
        }
        if (!hornInvolved && e.getClick() == ClickType.SWAP_OFFHAND) {
            hornInvolved = plugin.items().isPartyItem(p.getInventory().getItemInOffHand());
        }
        if (hornInvolved) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof PartyMenus.Holder) { e.setCancelled(true); return; }
        for (ItemStack it : e.getNewItems().values()) {
            if (plugin.items().isPartyItem(it)) { e.setCancelled(true); return; }
        }
    }

    // ---------- no solo queue while in a party ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (plugin.parties().get(p.getUniqueId()) == null) return;
        // check one tick later, once PvPCore has finished filling in its GUI session
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof ReanMenuHolder rh) {
                var s = rh.session();
                if (s.context() == GuiContext.QUEUE || "queue-duels".equals(s.currentGuiId())) {
                    p.closeInventory();
                    plugin.send(p, "&cYou can't queue while in a party. Leave your party first.");
                }
            }
        });
    }
}
