package me.arboriginal.SimpleCompass.plugin;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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
  private SimpleCompass       plugin;
  private HashMap<UUID, Long> locks;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public Listeners(SimpleCompass main) {
    plugin = main;
    locks  = new HashMap<UUID, Long>();
  }

  // ----------------------------------------------------------------------------------------------
  // Listener methods
  // ----------------------------------------------------------------------------------------------

  @EventHandler
  public void onEntityPickupItem(EntityPickupItemEvent event) {
    if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

    plugin.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getEntity());
  }

  @EventHandler
  public void onEntityToggleGlide(EntityToggleGlideEvent event) {
    if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

    plugin.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getEntity());
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    InventoryHolder holder = event.getInventory().getHolder();

    if (!(holder instanceof Player)) return;

    plugin.tasks.set(TasksTypes.REFRESH_STATUS, (Player) holder);
  }

  @EventHandler
  public void onInventoryOpen(InventoryOpenEvent event) {
    if (event.isCancelled()) return;

    plugin.tasks.clear(TasksTypes.REFRESH_STATUS, (Player) event.getPlayer());
  }

  @EventHandler
  public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
    if (event.isCancelled()) return;

    plugin.compasses.commandTrigger(event.getMessage().substring(1));
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    plugin.cache.init(player.getUniqueId());
    plugin.trackers.loadTrackers(player);
    plugin.tasks.set(TasksTypes.REFRESH_STATUS, player);
    plugin.tasks.set(TasksTypes.FIX_UUID, player);
  }

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.isCancelled()) return;

    Player player = event.getPlayer();
    UUID   uid    = player.getUniqueId();
    Long   now    = CacheUtil.now();

    if (locks.containsKey(uid) && locks.get(uid) > now) return;

    locks.put(uid, now + plugin.config.getInt("delays.update_compass"));
    plugin.compasses.refreshCompassDatas(event.getPlayer());
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID   uid    = player.getUniqueId();

    locks.remove(uid);
    plugin.tasks.clear(player);
    plugin.trackers.unloadTrackers(player);
    plugin.cache.clear(uid);
  }

  @EventHandler
  public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
    if (event.isCancelled()) return;

    plugin.tasks.set(TasksTypes.REFRESH_STATUS, event.getPlayer());
  }

  @EventHandler
  public void onVehicleEnter(VehicleEnterEvent event) {
    if (event.isCancelled() || !(event.getEntered() instanceof Player)) return;

    plugin.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getEntered());
  }

  @EventHandler
  public void onVehicleExit(VehicleExitEvent event) {
    if (event.isCancelled() || !(event.getExited() instanceof Player)) return;

    plugin.tasks.set(TasksTypes.REFRESH_STATUS, (Player) event.getExited());
  }

  @EventHandler
  public void onServerCommand(ServerCommandEvent event) {
    if (event.isCancelled()) return;

    plugin.compasses.commandTrigger(event.getCommand());
  }
}
