package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Arena;
import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class MatchManager {
    private final PvPCoreParty plugin;
    private final Map<UUID, PartyMatch> byPlayer = new HashMap<>();
    private final Set<PartyMatch> active = new HashSet<>();

    public MatchManager(PvPCoreParty plugin) { this.plugin = plugin; }

    public PartyMatch get(UUID id) { return byPlayer.get(id); }
    public boolean inMatch(UUID id) { return byPlayer.containsKey(id); }

    /** Team Battle: splits one party's own members into Red and Blue, who then fight each other. */
    public boolean start(Party party, Kit kit) {
        List<UUID> all = new ArrayList<>(party.members());
        if (!allFreeAndOnline(all)) return false;
        Collections.shuffle(all);
        List<UUID> red = new ArrayList<>();
        List<UUID> blue = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) (i % 2 == 0 ? red : blue).add(all.get(i));
        Optional<Arena> arena = plugin.core().arenaManager().findAvailable();
        if (arena.isEmpty()) return false;
        Arena ar = arena.get();
        plugin.core().arenaManager().markUsed(ar);
        for (UUID id : all) plugin.core().queueManager().leaveAllQuiet(id);
        TeamMatch m = new TeamMatch(plugin, this, ar, kit, red, blue);
        active.add(m);
        for (UUID id : all) byPlayer.put(id, m);
        m.begin();
        return true;
    }

    /** Party FFA: one party's own members, all against each other, last one alive wins. */
    public boolean startFFA(Party party, Kit kit) {
        List<UUID> all = new ArrayList<>(party.members());
        if (!allFreeAndOnline(all)) return false;
        Optional<Arena> arena = plugin.core().arenaManager().findAvailable();
        if (arena.isEmpty()) return false;
        Arena ar = arena.get();
        plugin.core().arenaManager().markUsed(ar);
        for (UUID id : all) plugin.core().queueManager().leaveAllQuiet(id);
        FFAMatch m = new FFAMatch(plugin, this, ar, kit, all);
        active.add(m);
        for (UUID id : all) byPlayer.put(id, m);
        m.begin();
        return true;
    }

    private boolean allFreeAndOnline(List<UUID> all) {
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return false;
            if (plugin.core().duelManager().isInMatch(id) || byPlayer.containsKey(id)) return false;
        }
        return true;
    }

    /** Called by a match when it is fully finished. */
    void unregister(PartyMatch m, Collection<UUID> players) {
        active.remove(m);
        for (UUID id : players) byPlayer.remove(id, m);
    }

    /** A player quit / left their party while possibly in a match. */
    public void handleLeave(UUID id) {
        PartyMatch m = byPlayer.get(id);
        if (m != null) m.handleQuit(id);
    }

    public void endAll() {
        for (PartyMatch m : new ArrayList<>(active)) m.forceEnd();
    }
}
