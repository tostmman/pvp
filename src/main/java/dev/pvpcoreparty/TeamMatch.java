package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Arena;
import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** One party's own members split red vs blue, single round, in a PvPCore arena with a PvPCore kit. */
public final class TeamMatch implements PartyMatch {
    private final PvPCoreParty plugin;
    private final MatchManager manager;
    private final Arena arena;
    private final Kit kit;
    private final List<UUID> red;
    private final List<UUID> blue;
    private final Set<UUID> present = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> everyone = new HashSet<>();
    private final MatchScoreboard board = new MatchScoreboard("&c&lRED &7vs &9&lBLUE");
    private GateWall redGate, blueGate;
    private State state = State.BETWEEN;
    private BukkitTask task;
    private boolean cleaned;

    TeamMatch(PvPCoreParty plugin, MatchManager manager, Arena arena, Kit kit, List<UUID> red, List<UUID> blue) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
        this.kit = kit;
        this.red = red;
        this.blue = blue;
        present.addAll(red);
        present.addAll(blue);
        everyone.addAll(present);
    }

    @Override public State state() { return state; }
    @Override public Kit kit() { return kit; }
    public boolean isRed(UUID id) { return red.contains(id); }
    @Override public boolean sameTeam(UUID a, UUID b) { return (isRed(a) && isRed(b)) || (blue.contains(a) && blue.contains(b)); }
    @Override public boolean isAlive(UUID id) { return alive.contains(id); }
    @Override public Location spectatorSpawn() { return arena.redSpawn(); }

    void begin() {
        broadcast("&aTeam Battle: &c" + red.size() + " &7vs &9" + blue.size() + " &7| Kit: &e" + kit.displayName()
                + " &7| Arena: &e" + arena.displayName());
        board.show(everyone);
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.title(p, isRed(id) ? "&c&lRED &7vs &9&lBLUE" : "&9&lBLUE &7vs &c&lRED", "&eKit: " + kit.displayName());
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f); // "the gate is sealed"
        }
        startRound();
    }

    private void startRound() {
        if (state == State.ENDED) return;
        alive.clear();
        for (UUID id : present) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            alive.add(id);
            prep(p, isRed(id) ? arena.redSpawn() : arena.blueSpawn());
        }
        spawnGates();
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
        broadcast("&7" + (isRed(victim.getUniqueId()) ? "&c" : "&9") + victim.getName() + " &7was eliminated"
                + (killer != null ? " by &f" + killer.getName() : "") + "&7.");
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
        boolean redLeft = red.stream().anyMatch(present::contains);
        boolean blueLeft = blue.stream().anyMatch(present::contains);
        if (!redLeft || !blueLeft) finish(redLeft);
        else if (state == State.FIGHTING) checkRoundEnd();
    }

    private void checkRoundEnd() {
        if (state != State.FIGHTING) return;
        boolean redAlive = red.stream().anyMatch(alive::contains);
        boolean blueAlive = blue.stream().anyMatch(alive::contains);
        if (redAlive && blueAlive) return;
        finish(redAlive);
    }

    private void finish(boolean redWon) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        if (task != null) task.cancel();
        broadcast("&e&lMATCH OVER! " + (redWon ? "&c&lRED" : "&9&lBLUE") + " &ewins!");
        updateBoard();
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.title(p, redWon ? "&c&lRED WINS" : "&9&lBLUE WINS", "&7gg");
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

    private void spawnGates() {
        String mat = plugin.getConfig().getString("door.material", "IRON_BARS");
        int width = Math.max(1, plugin.getConfig().getInt("door.width", 3));
        int height = Math.max(1, plugin.getConfig().getInt("door.height", 3));
        Material material;
        try { material = Material.valueOf(mat.toUpperCase(java.util.Locale.ROOT)); } catch (Exception ex) { material = Material.IRON_BARS; }
        if (!plugin.getConfig().getBoolean("door.enabled", true)) return;
        redGate = new GateWall(arena.redSpawn(), material, width, height);
        blueGate = new GateWall(arena.blueSpawn(), material, width, height);
    }

    private void openGates() {
        if (redGate != null) redGate.open(plugin);
        if (blueGate != null) blueGate.open(plugin);
    }

    private void removeGatesInstant() {
        if (redGate != null) redGate.removeInstant();
        if (blueGate != null) blueGate.removeInstant();
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

    private void updateBoard() {
        long redAlive = red.stream().filter(alive::contains).count();
        long blueAlive = blue.stream().filter(alive::contains).count();
        board.update(List.of(
                "&7Kit: &e" + kit.displayName(),
                "",
                "&c&lRED &7remaining: &f" + redAlive + "&7/" + red.size(),
                "&9&lBLUE &7remaining: &f" + blueAlive + "&7/" + blue.size()
        ));
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
