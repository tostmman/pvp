package dev.pvpcoreparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The party item that sits in hotbar slot 2 of the lobby.
 * No party / regular member -> a goat horn (view/create/join/leave).
 * Party LEADER -> a lectern instead, which is the only way to reach the full leader menu
 * (invite, members, party battle, disband). A non-leader never receives the lectern.
 */
public final class PartyItems {
    public static final int SLOT = 1; // hotbar slot 2 (0-indexed)

    private final PvPCoreParty plugin;
    private final NamespacedKey key;
    private final NamespacedKey leaderKey;

    public PartyItems(PvPCoreParty plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "party_item");
        this.leaderKey = new NamespacedKey(plugin, "party_item_leader");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private ItemStack hornItem() {
        ItemStack it = new ItemStack(Material.GOAT_HORN);
        ItemMeta m = it.getItemMeta();
        m.displayName(PvPCoreParty.plain("&d🎺 &5Party &7(Right Click)"));
        m.lore(List.of(PvPCoreParty.plain("&7Create or manage your party."),
                PvPCoreParty.plain("&7Invite friends and fight other parties.")));
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack lecternItem() {
        ItemStack it = new ItemStack(Material.LECTERN);
        ItemMeta m = it.getItemMeta();
        m.displayName(PvPCoreParty.plain("&6📖 &eParty Leader Menu &7(Right Click)"));
        m.lore(List.of(PvPCoreParty.plain("&7Invite, manage members,"),
                PvPCoreParty.plain("&7start a party battle, or disband."),
                PvPCoreParty.plain("&8Only you can see this item.")));
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(leaderKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isPartyItem(ItemStack it) {
        return it != null && it.getType() != Material.AIR && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean isLeaderItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(leaderKey, PersistentDataType.BYTE);
    }

    /** Which item (horn or lectern) a player should currently hold in slot 2. */
    private ItemStack expectedItem(Player p) {
        dev.pvpcoreparty.Party party = plugin.parties().get(p.getUniqueId());
        boolean leader = party != null && party.isLeader(p.getUniqueId());
        return leader ? lecternItem() : hornItem();
    }

    /** Keeps the right party item in slot 2 for players who are in the PvPCore lobby. */
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            ItemStack first = inv.getItem(0);
            boolean lobby = first != null && first.getType() != Material.AIR
                    && plugin.core().lobbyItems().isLobbyItem(first)
                    && !plugin.matches().inMatch(p.getUniqueId())
                    && !plugin.core().duelManager().isInMatch(p.getUniqueId());
            ItemStack cur = inv.getItem(SLOT);
            boolean hasPartyItem = isPartyItem(cur);
            if (!lobby) {
                if (hasPartyItem) inv.setItem(SLOT, null);
                continue;
            }
            if (!hasPartyItem) {
                if (cur == null || cur.getType() == Material.AIR) inv.setItem(SLOT, expectedItem(p));
                continue;
            }
            // already holding a party item: make sure it's the right one for the current role
            boolean shouldBeLeader = isLeaderItem(expectedItem(p));
            if (isLeaderItem(cur) != shouldBeLeader) inv.setItem(SLOT, expectedItem(p));
        }
    }
}
