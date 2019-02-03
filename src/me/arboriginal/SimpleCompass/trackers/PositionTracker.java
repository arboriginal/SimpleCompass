package me.arboriginal.SimpleCompass.trackers;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class PositionTracker extends AbstractTracker {
  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public PositionTracker(SimpleCompass plugin) {
    super(plugin);
  }

  // ----------------------------------------------------------------------------------------------
  // Actions methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<TrackingActions> getActionsAvailable(Player player, boolean keepUnavailable) {
    List<TrackingActions> list = super.getActionsAvailable(player, keepUnavailable);

    if (player.hasPermission("scompass.track." + trackerID() + ".manage")) {
      list.add(TrackingActions.ADD);
      list.add(TrackingActions.DEL);
    }

    if (keepUnavailable || !sc.config.getConfigurationSection("positions").getKeys(false).isEmpty()) {
      list.add(TrackingActions.START);
      list.add(TrackingActions.STOP);
    }

    return list;
  }

  public TargetSelector requireTarget(TrackingActions action) {
    return action.equals(TrackingActions.ADD) ? TargetSelector.NEWCOORDS : super.requireTarget(action);
  }

  // ----------------------------------------------------------------------------------------------
  // Tracker methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public String trackerID() {
    return "POSITION";
  }

  // ----------------------------------------------------------------------------------------------
  // Targets methods
  // ----------------------------------------------------------------------------------------------

  public List<String> availableTargets(Player player, String startWith) {
    List<String> list = new ArrayList<String>();

    datas().getKeys(false).forEach(candidate -> {
      if (startWith.isEmpty() || candidate.toLowerCase().startsWith(startWith.toLowerCase())) list.add(candidate);
    });

    return filterByPermission(player, list);
  }

  @Override
  public List<String> list(Player player, TrackingActions action, String startWith) {
    return filterByPermission(player, super.list(player, action, startWith));
  }

  // ----------------------------------------------------------------------------------------------
  // Command methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean perform(Player player, String command, TrackingActions action, String target, String[] args) {
    if (target == null && !action.equals(TrackingActions.ADD)) return false;

    switch (action) {
      case ADD:
        if (args.length < 3)
          sc.sendMessage(player, "missing_target");
        else if (set(player, args[2], getCoords(player, args))) {
          player.sendMessage(sc.prepareMessage("commands." + command + "." + trackerID() + ".ADD",
              ImmutableMap.of("target", args[2])));

          if (sc.config.getBoolean("tracker_settings.trackers." + trackerID() + ".auto_activated"))
            activate(player, args[2], false);
        }
        else
          player.sendMessage(sc.prepareMessage("target_exists", ImmutableMap.of("target", args[2])));
        break;

      case DEL:
        player.sendMessage(sc.prepareMessage(del(player, args[2])
            ? "commands." + command + "." + trackerID() + ".DEL"
            : "target_not_found",
            ImmutableMap.of("target", args[2])));
        break;

      case START:
        if (!activate(player, args[2], true)) break;

        sc.sendMessage(player, "commands." + command + "." + trackerID() + ".START",
            ImmutableMap.of("target", args[2]));
        break;

      case STOP:
        disable(player, args[2]);

        sc.sendMessage(player, "commands." + command + "." + trackerID() + ".STOP",
            ImmutableMap.of("target", args[2]));
        break;

      default:
        return false;
    }

    return true;
  }

  // ----------------------------------------------------------------------------------------------
  // Targets methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean set(Player player, String name, double[] coords) {
    String key = key(player, name);

    if (datas().contains(key)) return false;
    if (limitReached(player, TrackingActions.ADD, true, datas().getKeys(false).size())) return false;

    boolean success = (save(key + ".x", coords[0]) && save(key + ".z", coords[1]));
    if (success) return true;

    save(key, null);
    return false;
  }

  // ----------------------------------------------------------------------------------------------
  // Storage methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public MemorySection datas() {
    return (MemorySection) sc.config.getConfigurationSection("positions");
  }

  @Override
  public String key(Player player, String name) {
    return (name == null ? "" : name.toLowerCase());
  }

  @Override
  public boolean save(String key, Object value) {
    if (key.isEmpty()) return false;
    datas().set(key, value);
    return true;
  }

  // ----------------------------------------------------------------------------------------------
  // Specific methods
  // ----------------------------------------------------------------------------------------------

  private List<String> filterByPermission(Player player, List<String> list) {
    if (player.hasPermission("scompass.track.POSITION.defined.*")) return list;

    for (String name : list)
      if (!player.hasPermission("scompass.track.POSITION.defined." + name)) list.remove(name);

    return list;
  }

  private double[] getCoords(Player player, String[] args) {
    double[] coords = null;

    if (args.length == 5)
      try {
        coords = new double[] { Double.parseDouble(args[3]), Double.parseDouble(args[4]) };
      }
      catch (Exception e) {}

    if (coords == null)
      coords = new double[] { player.getLocation().getX(), player.getLocation().getZ() };

    return coords;
  }
}
