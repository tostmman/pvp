package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.function.BiConsumer;

public final class PartyMenus {
    /** Holder that carries the click actions of one open menu. */
    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
        public BiConsumer<Player, ClickType> action(int slot) { return actions.get(slot); }
    }

    private final PvPCoreParty plugin;

    public PartyMenus(PvPCoreParty plugin) { this.plugin = plugin; }

    // ---------- tiny helpers ----------

    private void later(Runnable r) { Bukkit.getScheduler().runTask(plugin, r); }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(PvPCoreParty.plain(name));
        if (lore.length > 0) {
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String s : lore) l.add(PvPCoreParty.plain(s));
            m.lore(l);
        }
        it.setItemMeta(m);
        return it;
    }

    private ItemStack head(UUID id, String name, String... lore) {
        ItemStack it = item(Material.PLAYER_HEAD, name, lore);
        if (it.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            it.setItemMeta(sm);
        }
        return it;
    }

    private Holder create(int size, String title, Inventory[] out) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, size, PvPCoreParty.color(title));
        out[0] = h.inv;
        return h;
    }

    private void set(Holder h, int slot, ItemStack it, BiConsumer<Player, ClickType> action) {
        h.inv.setItem(slot, it);
        if (action != null) h.actions.put(slot, action);
    }

    private void fillRest(Holder h) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < h.inv.getSize(); i++) if (h.inv.getItem(i) == null) h.inv.setItem(i, pane);
    }

    private void back(Holder h, int slot) {
        set(h, slot, item(Material.ARROW, "&7« Back"), (p, c) -> later(() -> openMain(p)));
    }

    // ---------- main menu ----------

    public void openMain(Player p) {
        PartyManager pm = plugin.parties();
        UUID id = p.getUniqueId();
        Party party = pm.get(id);
        Holder h = create(27, "&8Party", new Inventory[1]);

        if (party == null) {
            set(h, 11, item(Material.NETHER_STAR, "&aCreate Party", "&7Start a party and invite players.",
                    "&7You can't queue while in a party."),
                    (pl, c) -> { pm.create(pl); later(() -> openMain(pl)); });
            String inviter = pm.pendingInviteFrom(id);
            if (inviter != null) {
                set(h, 15, item(Material.LIME_DYE, "&aAccept invite", "&7From: &e" + inviter),
                        (pl, c) -> { pm.accept(pl); later(() -> openMain(pl)); });
            } else {
                set(h, 15, item(Material.GRAY_DYE, "&7No pending invites"), null);
            }
        } else {
            boolean leader = party.isLeader(id);
            int max = plugin.getConfig().getInt("max-party-size", 4);
            set(h, 4, item(Material.PAPER, "&aYour Party &7(" + party.size() + "/" + max + ")",
                    leader ? "&7You are the &6leader&7." : "&7Leader: &e" + nameOf(party.leader())), null);

            if (leader) {
                int secs = plugin.getConfig().getInt("invite-timeout-seconds", 60);
                set(h, 10, item(Material.WRITABLE_BOOK, "&aInvite Player", "&7Use &e/p invite <player>",
                        "&7Invites expire after " + secs + " seconds.", "&eClick for the command."),
                        (pl, c) -> {
                            later(pl::closeInventory);
                            pl.sendMessage(PvPCoreParty.color(plugin.getConfig().getString("prefix", "") + "&7Type &e/p invite <player> &7(click here)")
                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/p invite ")));
                        });
                set(h, 14, item(Material.DIAMOND_SWORD, "&cParty Battle", "&7Team battle vs another party,",
                        "&7or FFA among your own members."),
                        (pl, c) -> later(() -> openGamemodeSelect(pl)));
                set(h, 22, item(Material.BARRIER, "&cDisband Party", "&7Removes everyone from the party.",
                        "&eClick to confirm."),
                        (pl, c) -> later(() -> openConfirm(pl)));
            } else {
                set(h, 10, item(Material.GRAY_DYE, "&7Invite Player", "&8Only the leader can invite."), null);
                set(h, 14, item(Material.GRAY_DYE, "&7Party Battle", "&8Only the leader can start battles."), null);
            }
            set(h, 12, item(Material.BOOK, "&bMembers", leader ? "&7Promote or kick members." : "&7See your party."),
                    (pl, c) -> later(() -> openMembers(pl)));
            if (!leader) {
                set(h, 16, item(Material.OAK_DOOR, "&6Leave Party", "&eClick to confirm."),
                        (pl, c) -> later(() -> openConfirm(pl)));
            }
        }
        fillRest(h);
        p.openInventory(h.inv);
    }

    // ---------- confirmation (cake) for disband / leave ----------

    public void openConfirm(Player p) {
        Party party = plugin.parties().get(p.getUniqueId());
        if (party == null) { plugin.send(p, "&cYou are not in a party."); return; }
        boolean leader = party.isLeader(p.getUniqueId());
        Holder h = create(27, leader ? "&4Disband party?" : "&6Leave party?", new Inventory[1]);
        set(h, 4, item(leader ? Material.BARRIER : Material.OAK_DOOR, leader ? "&cDisband Party" : "&6Leave Party",
                leader ? "&7Everyone will be removed from the party." : "&7You will leave the party."), null);
        set(h, 13, item(Material.CAKE, "&a&lClick the cake to confirm",
                leader ? "&7Disband the party for everyone." : "&7Leave your party."),
                (pl, c) -> {
                    if (leader) plugin.parties().disband(pl); else plugin.parties().leave(pl);
                    later(pl::closeInventory);
                });
        set(h, 22, item(Material.ARROW, "&c« Cancel"), (pl, c) -> later(() -> openMain(pl)));
        fillRest(h);
        p.openInventory(h.inv);
    }

    // ---------- members menu ----------

    private void openMembers(Player p) {
        PartyManager pm = plugin.parties();
        Party party = pm.get(p.getUniqueId());
        if (party == null) { openMain(p); return; }
        boolean leader = party.isLeader(p.getUniqueId());
        Holder h = create(27, "&8Party members", new Inventory[1]);
        int slot = 9;
        for (UUID m : party.members()) {
            if (slot >= 18) break;
            String n = nameOf(m);
            boolean isLeader = party.isLeader(m);
            if (leader && !m.equals(p.getUniqueId())) {
                set(h, slot++, head(m, (isLeader ? "&6★ " : "&f") + n, "&aLeft-click: &7promote to leader", "&cRight-click: &7kick"),
                        (pl, c) -> {
                            if (c.isLeftClick()) pm.promote(pl, n);
                            else if (c.isRightClick()) pm.kick(pl, n);
                            later(() -> openMembers(pl));
                        });
            } else {
                set(h, slot++, head(m, (isLeader ? "&6★ " : "&f") + n), null);
            }
        }
        back(h, 22);
        fillRest(h);
        p.openInventory(h.inv);
    }

    // ---------- party battle menus ----------

    // ---------- gamemode select (like mcpvp.club's) ----------

    public void openGamemodeSelect(Player p) {
        Party mine = plugin.parties().get(p.getUniqueId());
        if (mine == null || !mine.isLeader(p.getUniqueId())) { openMain(p); return; }
        Holder h = create(27, "&8Select a gamemode", new Inventory[1]);
        int teamMin = Math.max(2, plugin.getConfig().getInt("gamemodes.team.min-players", 2));
        if (mine.size() >= teamMin) {
            set(h, 11, item(Material.RED_WOOL, "&c&lTeam Battle", "&7Splits your party into",
                    "&c&lRED &7and &9&lBLUE&7, they fight each other."),
                    (pl, c) -> later(() -> openKits(pl)));
        } else {
            set(h, 11, item(Material.GRAY_DYE, "&7Team Battle", "&8Needs at least " + teamMin + " party members."), null);
        }
        int ffaMin = Math.max(2, plugin.getConfig().getInt("gamemodes.ffa.min-players", 2));
        if (mine.size() >= ffaMin) {
            set(h, 15, item(Material.TNT, "&e&lParty FFA", "&7Everyone in your party fights,", "&7last one standing wins."),
                    (pl, c) -> later(() -> openFFAKits(pl)));
        } else {
            set(h, 15, item(Material.GRAY_DYE, "&7Party FFA", "&8Needs at least " + ffaMin + " party members."), null);
        }
        back(h, 22);
        fillRest(h);
        p.openInventory(h.inv);
    }

    private void openKits(Player p) {
        List<Kit> kits = plugin.parties().allowedKits("team");
        Holder h = create(54, "&8Pick a kit - Team Battle", new Inventory[1]);
        int slot = 0;
        for (Kit k : kits) {
            if (slot >= 45) break;
            ItemStack icon = k.icon() != null ? k.icon().clone() : new ItemStack(Material.IRON_SWORD);
            ItemMeta m = icon.getItemMeta();
            m.displayName(PvPCoreParty.plain("&b" + k.displayName()));
            m.lore(List.of(PvPCoreParty.plain("&7Click to split the party and start.")));
            icon.setItemMeta(m);
            String kid = k.id();
            set(h, slot++, icon, (pl, c) -> {
                plugin.parties().startTeamBattle(pl, kid);
                later(pl::closeInventory);
            });
        }
        set(h, 49, item(Material.ARROW, "&7« Back"), (pl, c) -> later(() -> openGamemodeSelect(pl)));
        fillRest(h);
        p.openInventory(h.inv);
    }

    private void openFFAKits(Player p) {
        List<Kit> kits = plugin.parties().allowedKits("ffa");
        Holder h = create(54, "&8Pick a kit - Party FFA", new Inventory[1]);
        int slot = 0;
        for (Kit k : kits) {
            if (slot >= 45) break;
            ItemStack icon = k.icon() != null ? k.icon().clone() : new ItemStack(Material.IRON_SWORD);
            ItemMeta m = icon.getItemMeta();
            m.displayName(PvPCoreParty.plain("&b" + k.displayName()));
            m.lore(List.of(PvPCoreParty.plain("&7Click to start the FFA.")));
            icon.setItemMeta(m);
            String kid = k.id();
            set(h, slot++, icon, (pl, c) -> {
                plugin.parties().startFFA(pl, kid);
                later(pl::closeInventory);
            });
        }
        set(h, 49, item(Material.ARROW, "&7« Back"), (pl, c) -> later(() -> openGamemodeSelect(pl)));
        fillRest(h);
        p.openInventory(h.inv);
    }

    private String nameOf(UUID id) {
        Player o = Bukkit.getPlayer(id);
        if (o != null) return o.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "?";
    }
}
