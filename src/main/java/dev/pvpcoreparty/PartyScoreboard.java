package dev.pvpcoreparty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A simple per-player sidebar scoreboard shown while a player is in a party match
 * (Team Battle / Party FFA), in the same "title + lines" shape PvPCore's own sidebar uses.
 * Whatever scoreboard the player had before (PvPCore's own) is restored once the match ends.
 */
public final class PartyScoreboard {
    // unique entry keys (not shown to the player - the visible text is set via customName)
    private static final String[] ENTRIES = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private final Map<UUID, Scoreboard> previous = new HashMap<>();

    public PartyScoreboard(PvPCoreParty plugin) { /* no state needed yet, kept for symmetry with other managers */ }

    public void show(Player p, String titleLegacy, List<String> linesLegacy) {
        previous.putIfAbsent(p.getUniqueId(), p.getScoreboard());
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("pcparty", Criteria.DUMMY, PvPCoreParty.plain(titleLegacy));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = Math.min(linesLegacy.size(), ENTRIES.length);
        for (int i = 0; i < score; i++) {
            Score s = obj.getScore(ENTRIES[i]);
            s.setScore(score - i);
            s.customName(PvPCoreParty.plain(linesLegacy.get(i)));
        }
        p.setScoreboard(sb);
    }

    public void restore(Player p) {
        Scoreboard sb = previous.remove(p.getUniqueId());
        if (!p.isOnline()) return;
        p.setScoreboard(sb != null ? sb : Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
