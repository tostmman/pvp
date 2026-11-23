package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Arena;
import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.*;

/** Free-for-all among one party's own members: everyone fights everyone, single round, last one alive wins. */
public final class FFAMatch implements PartyMatch {
    private final PvPCoreParty plugin;
    private final MatchManager manager;
    private final Arena arena;
    private final Kit kit;
    private final List<UUID> players;
    private final Set<UUID> present = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> everyone = new HashSet<>();
    private final MatchScoreboard board = new MatchScoreboard("&e&lPARTY FFA");
    private final List<GateWall> gates = new ArrayList<>();
    private State state = State.BETWEEN;
    private BukkitTask task;
    private boolean cleaned;

    FFAMatch(PvPCoreParty plugin, MatchManager manager, Arena arena, Kit kit, List<UUID> players) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
        this.kit = kit;
        this.players = players;
        present.addAll(players);
        everyone.addAll(players);
    }

    @Override public State state() { return state; }
    @Override public Kit kit() { return kit; }
    @Override public boolean sameTeam(UUID a, UUID b) { return false; } // everyone can hit everyone
    @Override public boolean isAlive(UUID id) { return alive.contains(id); }
    @Override public Location spectatorSpawn() { return arena.redSpawn(); }

    void begin() {
        broadcast("&aParty FFA: &e" + present.size() + " players &7| Kit: &e" + kit.displayName()
                + " &7| Arena: &e" + arena.displayName());
        board.show(everyone);
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.title(p, "&e&lFREE FOR ALL", "&7Last one standing wins! Kit: " + kit.displayName());
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f); // "the gate is sealed"
        }
        startRound();
    }

    private void startRound() {
        if (state == State.ENDED) return;
        alive.clear();
        List<UUID> list = new ArrayList<>(present);
        // NOTE: PvPCore arenas only expose two fixed spawn points (red/blue); for more than 2 players we
        // spread everyone in a small circle around the midpoint of those two spawns.
        Location base = arena.redSpawn() != null ? arena.redSpawn() : arena.blueSpawn();
        Location other = arena.blueSpawn() != null ? arena.blueSpawn() : arena.redSpawn();
        Location mid = base.clone().add(other).multiply(0.5);
        mid.setWorld(base.getWorld());
        double radius = Math.max(2.0, Math.min(4.0, 1.5 * list.size() / Math.PI));
        for (int i = 0; i < list.size(); i++) {
            UUID id = list.get(i);
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            alive.add(id);
            double angle = (2 * Math.PI * i) / Math.max(1, list.size());
            Location spawn = mid.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            Vector dir = mid.toVector().subtract(spawn.toVector());
            if (dir.lengthSquared() > 0.0001) {
                spawn.setDirection(dir);
            }
            prep(p, spawn);
            spawnGate(spawn);
        }
        state = State.COUNTDOWN;
        updateBoard();
        int[] left = { Math.max(1, plugin.core().settings().countdownSeconds()) };
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.COUNTDOWN) { task.cancel(); return; }
            if (left[0] <= 0) {
                state = State.FIGHTING;
                bar("&a&lFIGHT!");
                openGates();
                for (UUID id : present) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                }
                updateBoard();
                task.cancel();
                return;
            }
            bar("&e" + left[0]);
            for (UUID id : present) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            }
            left[0]--;
        }, 0L, 20L);
    }

    private void prep(Player p, Location spawn) {
        p.setGameMode(kit.gameMode() != null ? kit.gameMode() : GameMode.SURVIVAL);
        applyKit(p);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        for (var e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        if (spawn != null) p.teleport(spawn);
    }

    private void applyKit(Player p) {
        Kit k = plugin.core().kitManager().getEffectiveKit(p.getUniqueId(), kit);
        if (k == null) k = kit;
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack[] c = k.contents();
        if (c != null) for (int i = 0; i < Math.min(c.length, 36); i++) inv.setItem(i, c[i] == null ? null : c[i].clone());
        ItemStack[] a = k.armor();
        if (a != null) {
            ItemStack[] copy = new ItemStack[a.length];
            for (int i = 0; i < a.length; i++) copy[i] = a[i] == null ? null : a[i].clone();
            inv.setArmorContents(copy);
        }
        ItemStack off = k.offhand();
        if (off != null) inv.setItemInOffHand(off.clone());
    }

    @Override
    public void handleDeath(Player victim) {
        if (state != State.FIGHTING || !alive.remove(victim.getUniqueId())) return;
        Player killer = victim.getKiller();
        broadcast("&7" + victim.getName() + " &7was eliminated" + (killer != null ? " by &f" + killer.getName() : "")
                + "&7. &e(" + alive.size() + " left)");
        updateBoard();
        checkRoundEnd();
    }

    @Override
    public void makeSpectator(Player p) {
        if (state != State.ENDED && !alive.contains(p.getUniqueId())) p.setGameMode(GameMode.SPECTATOR);
    }

    @Override
    public void handleQuit(UUID id) {
        if (!present.remove(id)) return;
        alive.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) restore(p);
        manager.unregister(this, List.of(id));
        if (state == State.ENDED) return;
        updateBoard();
        checkRoundEnd();
    }

    private void checkRoundEnd() {
        if (state != State.FIGHTING) return;
        if (alive.size() > 1) return;
        UUID winner = alive.isEmpty() ? null : alive.iterator().next();
        finish(winner);
    }

    private void finish(UUID winner) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        if (task != null) task.cancel();
        String name = winner != null ? nameOf(winner) : null;
        broadcast(name != null ? "&e&lMATCH OVER! &a" + name + " &ewins the FFA!" : "&e&lMATCH OVER! &7No winner.");
        updateBoard();
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.title(p, id.equals(winner) ? "&a&lYOU WIN!" : (name != null ? "&e" + name + " &7wins" : "&7Match over"), "&7gg");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 60L);
    }

    @Override
    public void forceEnd() {
        if (state == State.ENDED) { cleanup(); return; }
        state = State.ENDED;
        if (task != null) task.cancel();
        cleanup();
    }

    private void updateBoard() {
        board.update(List.of(
                "&7Kit: &e" + kit.displayName(),
                "",
                "&e&lRemaining: &f" + alive.size() + "&7/" + present.size()
        ));
    }

    private void spawnGate(Location spawn) {
        if (!plugin.getConfig().getBoolean("door.enabled", true)) return;
        String mat = plugin.getConfig().getString("door.material", "IRON_BARS");
        int width = Math.max(1, plugin.getConfig().getInt("door.width", 3));
        int height = Math.max(1, plugin.getConfig().getInt("door.height", 3));
        Material material;
        try { material = Material.valueOf(mat.toUpperCase(java.util.Locale.ROOT)); } catch (Exception ex) { material = Material.IRON_BARS; }
        gates.add(new GateWall(spawn, material, width, height));
    }

    private void openGates() {
        for (GateWall g : gates) g.open(plugin);
    }

    private void removeGatesInstant() {
        for (GateWall g : gates) g.removeInstant();
    }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        removeGatesInstant();
        board.hide(everyone);
        for (UUID id : new ArrayList<>(present)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
        manager.unregister(this, everyone);
        try {
            plugin.core().arenaManager().regenArenaAsync(arena, () -> arena.setInUse(false));
        } catch (Throwable t) {
            arena.setInUse(false);
        }
    }

    private void restore(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        for (var e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (!p.isDead()) p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        Location lobby = plugin.core().settings().mainSpawn();
        if (lobby != null) p.teleport(lobby);
        plugin.core().lobbyItems().giveLobbyItems(p);
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "?";
    }

    private void broadcast(String legacy) {
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.send(p, legacy);
        }
    }

    private void bar(String legacy) {
        for (UUID id : present) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(PvPCoreParty.color(legacy));
        }
    }
}
