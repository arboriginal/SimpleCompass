package me.arboriginal.SimpleCompass;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class SimpleCompassListener implements Listener {
  private Main plugin;

  private HashMap<UUID, BukkitRunnable> tasks;

  public SimpleCompassListener(Main main) {
    plugin = main;
    tasks  = new HashMap<UUID, BukkitRunnable>();
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

    UUID uid = ((Player) event.getEntity()).getUniqueId();

    if (tasks.containsKey(uid)) {
      tasks.get(uid).cancel();
    }

    tasks.put(uid, new BukkitRunnable() {
      @Override
      public void run() {
        if (isCancelled()) return;

        tasks.remove(uid);
        plugin.scm.updateState((Player) event.getEntity());
      }
    });

    tasks.get(uid).runTaskLaterAsynchronously(plugin, 5);
  }

  @EventHandler
  public void onVehicleEnter(VehicleEnterEvent event) {
    if (event.isCancelled() || !(event.getEntered() instanceof Player)) return;

    plugin.scm.updateState((Player) event.getEntered());
  }

  @EventHandler
  public void onVehicleExit(VehicleExitEvent event) {
    if (event.isCancelled() || !(event.getExited() instanceof Player)) return;

    plugin.scm.updateState((Player) event.getExited());
  }

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.isCancelled()) return;

    plugin.scm.updateDatas(event.getPlayer());
  }
}
