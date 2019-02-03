package me.arboriginal.SimpleCompass.trackers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.CacheUtil;

public class DeathPosTracker extends AbstractTracker implements Listener {
  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public DeathPosTracker(SimpleCompass plugin) {
    super(plugin);

    sc.getServer().getPluginManager().registerEvents(this, sc);
  }

  // ----------------------------------------------------------------------------------------------
  // Listener methods
  // ----------------------------------------------------------------------------------------------

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player   player = event.getEntity();
    double[] coords = new double[] { player.getLocation().getX(), player.getLocation().getZ() };
    long     target = CacheUtil.now() + sc.config.getInt("delays.death_position") * 1000;

    if (set(player, "" + target, coords)
        && sc.config.getBoolean("tracker_settings.trackers." + trackerID() + ".auto_activated"))
      activate(player, "" + target, false);
  }

  // ----------------------------------------------------------------------------------------------
  // Actions methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<TrackingActions> getActionsAvailable(Player player, boolean keepUnavailable) {
    List<TrackingActions> list = super.getActionsAvailable(player, keepUnavailable);

    if (keepUnavailable || lastDeath(player) != null) {
      list.add(TrackingActions.START);
      list.add(TrackingActions.STOP);
    }

    return list;
  }

  @Override
  public TargetSelector requireTarget(TrackingActions action) {
    if (action.equals(TrackingActions.START) || action.equals(TrackingActions.STOP)) 
      return TargetSelector.NONE;
    
    return super.requireTarget(action);
  }

  // ----------------------------------------------------------------------------------------------
  // Tracker methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public String trackerID() {
    return "DEATH_POSITION";
  }

  // ----------------------------------------------------------------------------------------------
  // Targets methods
  // ----------------------------------------------------------------------------------------------

  public double[] get(Player player, String name) {
    double[] coords = super.get(player, name);

    if (coords != null)
      try {
        long until = Long.parseLong(name);

        if (CacheUtil.now() > until) return null;
      }
      catch (Exception e) {
        return null;
      }

    return coords;
  }

  @Override
  public List<String> list(Player player, TrackingActions action, String startWith) {
    List<String> list = new ArrayList<String>();

    for (String name : super.list(player, action, startWith)) {
      double[] coords = get(player, name);

      if (coords != null) list.add(sc.formatMessage(
          sc.locale.getString("commands.sctrack." + trackerID() + ".list_coord")
              .replace("{x}", "" + (int) coords[0]).replace("{z}", "" + (int) coords[1])));
    }

    return list;
  }

  public boolean set(Player player, String name, double[] coords) {
    if (super.set(player, name, coords)) {
      List<String> list = sc.datas.activeTargetsList(player, trackerID());

      if (!list.isEmpty()) list.forEach(target -> {
        if (!target.equals(name)) del(player, target);
      });

      return true;
    }

    return false;
  }

  // ----------------------------------------------------------------------------------------------
  // Command methods
  // ----------------------------------------------------------------------------------------------

  public List<String> commandSuggestions(Player player, String[] args, HashMap<String, Object> parsed) {
    return super.commandSuggestions(player, args, parsed);
  }

  @Override
  public boolean perform(Player player, String command, TrackingActions action, String target, String[] args) {
    if (args.length != 2 || target != null) return false;

    target = lastDeath(player);

    if (target == null) {
      sc.sendMessage(player, "target_not_found", ImmutableMap.of("target", trackerID()));
      return true;
    }

    switch (action) {
      case START:
        activate(player, target, false);
        sc.sendMessage(player, "commands." + command + "." + trackerID() + ".START");
        break;

      case STOP:
        disable(player, target);
        sc.sendMessage(player, "commands." + command + "." + trackerID() + ".STOP");
        break;

      default:
        return false;
    }

    return true;
  }

  // ----------------------------------------------------------------------------------------------
  // Specific methods
  // ----------------------------------------------------------------------------------------------

  private String lastDeath(Player player) {
    for (String name : super.list(player, null, "")) if (get(player, name) != null) return name;

    return null;
  }
}
