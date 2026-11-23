package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Kit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Common surface for a live match that a party can be in (team battle or FFA), so one set of
 *  listeners (deaths, damage, respawns, item-use lockdown...) can handle either kind the same way. */
public interface PartyMatch {
    enum State { COUNTDOWN, FIGHTING, BETWEEN, ENDED }

    State state();
    Kit kit();
    boolean isAlive(UUID id);
    /** Whether two participants are on the same side (no PvP / no friendly fire between them). */
    boolean sameTeam(UUID a, UUID b);
    Location spectatorSpawn();
    void handleDeath(Player victim);
    void makeSpectator(Player p);
    /** A participant disconnected or left their party mid-match. */
    void handleQuit(UUID id);
    void forceEnd();
}
