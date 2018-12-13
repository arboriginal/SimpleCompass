package me.arboriginal.SimpleCompass;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class SimpleCompassListener implements Listener {
  private Main plugin;

  private HashMap<UUID, BukkitRunnable> tasks;
  private HashMap<UUID, Long>           locks;

  public SimpleCompassListener(Main main) {
    plugin = main;
    tasks  = new HashMap<UUID, BukkitRunnable>();
    locks  = new HashMap<UUID, Long>();
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    plugin.scm.updateState(event.getPlayer());
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    plugin.scm.updateState(event.getPlayer());
  }

  @EventHandler
  public void onEntityToggleGlide(EntityToggleGlideEvent event) {
    if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

    delayedUpdate((Player) event.getEntity());
  }

  @EventHandler
  public void onVehicleEnter(VehicleEnterEvent event) {
    if (event.isCancelled() || !(event.getEntered() instanceof Player)) return;

    delayedUpdate((Player) event.getEntered());
  }

  @EventHandler
  public void onVehicleExit(VehicleExitEvent event) {
    if (event.isCancelled() || !(event.getExited() instanceof Player)) return;

    delayedUpdate((Player) event.getExited());
  }

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.isCancelled()) return;

    UUID uid = event.getPlayer().getUniqueId();
    Long now = plugin.getCurrentTime();

    if (locks.containsKey(uid) && locks.get(uid) > now) return;

    locks.put(event.getPlayer().getUniqueId(), now + Main.config.getInt("update_delay"));
    plugin.scm.updateDatas(event.getPlayer());
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    delayedUpdate((Player) event.getPlayer());
  }

  @EventHandler
  public void onInventoryOpen(InventoryOpenEvent event) {
    if (event.isCancelled()) return;

    cancelDelayedUpdate(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onEntityPickupItem(EntityPickupItemEvent event) {
    if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

    delayedUpdate((Player) event.getEntity());
  }

  // ----------------------------------------------------------------------------------------------
  // Custom private methods
  // ----------------------------------------------------------------------------------------------

  private void delayedUpdate(Player player) {
    UUID uid = player.getUniqueId();

    cancelDelayedUpdate(uid);

    tasks.put(uid, new BukkitRunnable() {
      @Override
      public void run() {
        if (isCancelled()) return;

        tasks.remove(uid);
        plugin.scm.updateState((Player) player);
      }
    });

    tasks.get(uid).runTaskLaterAsynchronously(plugin, 10);
  }

  private void cancelDelayedUpdate(UUID uid) {
    if (tasks.containsKey(uid)) {
      tasks.get(uid).cancel();
    }
  }
}
