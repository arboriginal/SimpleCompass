package me.arboriginal.SimpleCompass.trackers;

import java.util.List;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class CoordsTracker extends AbstractTracker {
  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public CoordsTracker(SimpleCompass plugin) {
    super(plugin);
  }

  //----------------------------------------------------------------------------------------------
  // Actions methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<TrackingActions> getActionsAvailable(Player player, boolean keepUnavailable) {
    List<TrackingActions> list = super.getActionsAvailable(player, keepUnavailable);

    list.add(TrackingActions.ADD);

    if (keepUnavailable || !list(player, null, "").isEmpty()) {
      list.add(TrackingActions.DEL);
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
    return "COORDS";
  }

  // ----------------------------------------------------------------------------------------------
  // Command methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean perform(Player player, String command, TrackingActions action, String target, String[] args) {
    if (target == null && !action.equals(TrackingActions.ADD)) {
      sc.sendMessage(player, "missing_target");
      return true;
    }

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
            ? "commands.sctrack." + trackerID() + ".DEL"
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
  // Specific methods
  // ----------------------------------------------------------------------------------------------

  private double[] getCoords(Player player, String[] args) {
    double[] coords = null;

    if (args.length == 5)
      try {
        coords = new double[] { Double.parseDouble(args[3]), Double.parseDouble(args[4]) };
      }
      catch (Exception e) {}

    if (coords == null) coords = new double[] { player.getLocation().getX(), player.getLocation().getZ() };

    return coords;
  }
}
