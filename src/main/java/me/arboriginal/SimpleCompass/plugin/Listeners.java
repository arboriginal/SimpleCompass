package me.arboriginal.SimpleCompass.plugin;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.InventoryHolder;
import me.arboriginal.SimpleCompass.managers.TaskManager.TasksTypes;
import me.arboriginal.SimpleCompass.utils.CacheUtil;

public class Listeners implements Listener {
    public SimpleCompass       sc;
    public HashMap<UUID, Long> locks;

    // Constructor methods ---------------------------------------------------------------------------------------------

    public Listeners(SimpleCompass plugin) {
        sc    = plugin;
        locks = new HashMap<UUID, Long>();
    }

    // Listener methods ------------------------------------------------------------------------------------------------

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.isCancelled() || !isPlayer(event.getEntity())) return;
        sc.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getEntity(), sc.config.getInt("delays.pickup_refresh"));
    }

    @EventHandler
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (event.isCancelled() || !isPlayer(event.getEntity())) return;
        sc.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getEntity());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Player) || !isPlayer((Player) holder)) return;
        sc.tasks.set(TasksTypes.REFRESH_STATUS, (Player) holder);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!event.isCancelled()) sc.tasks.clear(TasksTypes.REFRESH_STATUS, (Player) event.getPlayer());
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!event.isCancelled()) sc.compasses.commandTrigger(event.getMessage().substring(1));
    }

    @EventHandler
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        if (isPlayer(event.getPlayer())) sc.tasks.set(TasksTypes.REFRESH_STATUS, event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isPlayer(event.getEntity())) sc.compasses.removeCompass((Player) event.getEntity());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        sc.cache.init(player.getUniqueId());
        sc.targets.loadTargets(player);
        sc.tasks.set(TasksTypes.REFRESH_STATUS, player);
        sc.tasks.set(TasksTypes.FIX_UUID, player);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID   uid    = player.getUniqueId();
        Long   now    = CacheUtil.now();

        if (locks.containsKey(uid) && locks.get(uid) > now) return;

        locks.put(uid, now + sc.config.getInt("delays.update_compass"));
        sc.compasses.refreshCompassDatas(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID   uid    = player.getUniqueId();

        sc.compasses.removeCompass(player);
        locks.remove(uid);
        sc.tasks.clear(player);
        sc.targets.unloadTargets(player);
        sc.cache.clear(uid);
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!event.isCancelled()) sc.tasks.set(TasksTypes.REFRESH_STATUS, event.getPlayer());
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.isCancelled() || !isPlayer(event.getEntered())) return;
        sc.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getEntered());
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.isCancelled() || !isPlayer(event.getExited())) return;
        sc.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getExited());
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (!event.isCancelled()) sc.compasses.commandTrigger(event.getCommand());
    }

    // -----------------------------------------------------------------------------------------------
    // Private methods
    // -----------------------------------------------------------------------------------------------

    private boolean isPlayer(Entity entity) {
        return (entity instanceof Player) && !entity.hasMetadata("NPC");
    }
}
