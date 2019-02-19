package me.arboriginal.SimpleCompass.managers;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.compasses.BossbarCompass;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class TaskManager {
  private SimpleCompass                                      sc;
  private HashMap<TasksTypes, HashMap<UUID, BukkitRunnable>> tasks;

  public enum TasksTypes {
    FIX_UUID, REFRESH_ACTIONBAR, REFRESH_BOSSBAR, REFRESH_STATUS, REMOVEWARNING,
  }

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public TaskManager(SimpleCompass plugin) {
    sc    = plugin;
    tasks = new HashMap<TasksTypes, HashMap<UUID, BukkitRunnable>>();

    for (TasksTypes type : TasksTypes.values()) tasks.put(type, new HashMap<UUID, BukkitRunnable>());
  }

  // ----------------------------------------------------------------------------------------------
  // Tasks methods
  // ----------------------------------------------------------------------------------------------

  public void clear() {
    for (TasksTypes type : TasksTypes.values()) clear(type);
  }

  public void clear(Player player) {
    clear(player.getUniqueId());
  }

  public void clear(UUID uid) {
    for (TasksTypes type : TasksTypes.values()) clear(type, uid);
  }

  public void clear(TasksTypes type) {
    for (UUID uid : tasks.get(type).keySet()) clear(type, uid);
  }

  public void clear(TasksTypes type, Player player) {
    clear(type, player.getUniqueId());
  }

  public void clear(TasksTypes type, UUID uid) {
    clear(type, uid, get(type, uid));
  }

  public void clear(TasksTypes type, UUID uid, BukkitRunnable task) {
    if (task == null) task = get(type, uid);
    if (task != null) {
      task.cancel();
      tasks.get(type).remove(uid);
    }
  }

  public BukkitRunnable get(TasksTypes type, UUID uid) {
    return tasks.get(type).get(uid);
  }

  public void set(TasksTypes type, Player player) {
    set(type, player, null);
  }

  public void set(TasksTypes type, Player player, Object data) {
    UUID uid = player.getUniqueId();

    if (!sc.isReady) {
      if (data != null && data instanceof BossbarCompass) ((BossbarCompass) data).bossbar.removeAll();
      clear(type, uid);
      return;
    }

    BukkitRunnable task = null;

    switch (type) {
      case FIX_UUID:
        task = new BukkitRunnable() {
          @Override
          public void run() {
            if (isCancelled() || sc.compasses.getCompass(CompassTypes.BOSSBAR, uid) == null) return;
            sc.compasses.removeCompass(CompassTypes.BOSSBAR, player);
            sc.compasses.refreshCompassState(player);
            clear(type, uid, this);

            if (sc.trackers.isEmpty()) return;
            sc.trackers.forEach((trackerID, tracker) -> {
              for (String name : tracker.autoloadTargets(player, "")) tracker.activate(player, name, false);
            });
          }
        };

        task.runTaskLaterAsynchronously(sc, sc.config.getInt("delays.fix_uuid"));
        break;

      case REFRESH_ACTIONBAR:
      case REFRESH_BOSSBAR:
        CompassTypes compassType = CompassTypes.valueOf(type.toString().substring(8));

        task = new BukkitRunnable() {
          @Override
          public void run() {
            if (isCancelled()) return;
            sc.compasses.refreshCompass(player, compassType);
            clear(type, uid, this);
          }
        };

        task.runTaskLaterAsynchronously(sc, sc.config.getInt("delays.option_take_effect"));
        break;

      case REFRESH_STATUS:
        task = new BukkitRunnable() {
          @Override
          public void run() {
            if (isCancelled()) return;
            sc.compasses.refreshCompassState(player);
            clear(type, uid, this);
          }
        };

        task.runTaskLaterAsynchronously(sc,
            (data == null || !(data instanceof Integer)) ? sc.config.getInt("delays.refresh_status") : (int) data);
        break;

      case REMOVEWARNING:
        if (data == null || !(data instanceof BossbarCompass)) break;

        task = new BukkitRunnable() {
          @Override
          public void run() {
            if (isCancelled()) return;
            ((BossbarCompass) data).bossbar.removeAll();
            this.cancel();
          }
        };

        task.runTaskLaterAsynchronously(sc, sc.config.getInt("compass.BOSSBAR.warnPlayerNoMoreFuel") * 20);
        break;
    }

    if (task != null) {
      clear(type, uid);
      tasks.get(type).put(uid, task);
    }
  }
}
