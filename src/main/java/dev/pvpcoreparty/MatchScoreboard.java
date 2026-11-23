package dev.pvpcoreparty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/** A small sidebar scoreboard shown only to the players inside one match (team battle or party FFA). */
public final class MatchScoreboard {
    private final Scoreboard board;
    private final Objective obj;
    private final List<String> teamNames = new ArrayList<>();

    public MatchScoreboard(String titleLegacy) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        obj = board.registerNewObjective("pvpcoreparty", "dummy", PvPCoreParty.plain(titleLegacy));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /** Puts the given players on this scoreboard (they see only this sidebar until hide() is called). */
    public void show(Collection<UUID> players) {
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(board);
        }
    }

    /** Replaces the sidebar's lines, top to bottom. */
    public void update(List<String> lines) {
        for (String name : teamNames) {
            Team t = board.getTeam(name);
            if (t != null) t.unregister();
        }
        teamNames.clear();
        for (String entry : new ArrayList<>(board.getEntries())) board.resetScores(entry);

        int score = lines.size();
        for (int i = 0; i < lines.size() && i < 15; i++) {
            String teamName = "l" + i;
            Team t = board.registerNewTeam(teamName);
            String fakeEntry = ChatColor.values()[i % ChatColor.values().length].toString() + ChatColor.RESET;
            t.addEntry(fakeEntry);
            t.prefix(PvPCoreParty.plain(lines.get(i)));
            teamNames.add(teamName);
            obj.getScore(fakeEntry).setScore(score--);
        }
    }

    /** Puts the given players back on the server's default scoreboard. */
    public void hide(Collection<UUID> players) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(main);
        }
    }
}
