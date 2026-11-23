package dev.pvpcoreparty;

import dev.rean.pvpcore.PvPCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public final class PvPCoreParty extends JavaPlugin {
    private PvPCorePlugin core;
    private PartyManager parties;
    private MatchManager matches;
    private PartyMenus menus;
    private PartyItems items;
    private MatchGuard guard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = PvPCorePlugin.getInstance();
        if (core == null) {
            getLogger().severe("PvPCore is not loaded. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        matches = new MatchManager(this);
        parties = new PartyManager(this);
        menus = new PartyMenus(this);
        items = new PartyItems(this);
        PartyCommand cmd = new PartyCommand(this);
        getCommand("party").setExecutor(cmd);
        getCommand("party").setTabCompleter(cmd);
        getCommand("partychat").setExecutor(cmd);
        getServer().getPluginManager().registerEvents(new PartyListener(this), this);
        guard = new MatchGuard(this);
        getServer().getPluginManager().registerEvents(guard, this);
    }

    @Override
    public void onDisable() {
        if (matches != null) matches.endAll();
        if (guard != null) guard.shutdown();
    }

    public PvPCorePlugin core() { return core; }
    public PartyManager parties() { return parties; }
    public MatchManager matches() { return matches; }
    public PartyMenus menus() { return menus; }
    public PartyItems items() { return items; }

    public static Component color(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    /** Legacy-colored component with italics turned off (for item names/lore). */
    public static Component plain(String legacy) {
        return color(legacy).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    public void send(CommandSender to, String legacy) {
        to.sendMessage(color(getConfig().getString("prefix", "") + legacy));
    }

    /** Plays a configured sound (config path sounds.<key>, volume/pitch sounds.<key>-volume/-pitch) to each
     *  player, skipped entirely if the config value is missing/blank/NONE or not a real Sound name. */
    public void playSound(Iterable<UUID> ids, String key) {
        String name = getConfig().getString("sounds." + key);
        if (name == null || name.isBlank() || name.equalsIgnoreCase("NONE")) return;
        Sound sound;
        try {
            sound = Sound.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Unknown sound '" + name + "' for sounds." + key + " in config.yml");
            return;
        }
        float volume = (float) getConfig().getDouble("sounds." + key + "-volume", 1.0);
        float pitch = (float) getConfig().getDouble("sounds." + key + "-pitch", 1.0);
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    /** Short title + subtitle, same feel as PvPCore's own match titles. */
    public void title(Player p, String mainLegacy, String subLegacy) {
        p.showTitle(net.kyori.adventure.title.Title.title(
                plain(mainLegacy), plain(subLegacy),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(500))));
    }
}
