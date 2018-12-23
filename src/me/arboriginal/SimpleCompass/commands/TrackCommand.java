package me.arboriginal.SimpleCompass.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.managers.TrackerManager.TrackerTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class TrackCommand implements CommandExecutor, TabCompleter {
  private SimpleCompass plugin;

  public enum TrackingActions {
    ADD, ASK, DEL, START, STOP,
  }

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public TrackCommand(SimpleCompass main) {
    plugin = main;
  }

  // ----------------------------------------------------------------------------------------------
  // CommandExecutor methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player)) {
      plugin.sendMessage(sender, "command_only_for_players");
      return true;
    }

    if (args.length == 0 || (args.length > 2 && args[1].equals("help"))) return false;

    HashMap<String, Object> cmdArgs = getCommandArguments((Player) sender, args);

    TrackerTypes    tracker = (TrackerTypes) cmdArgs.get("tracker");
    TrackingActions action  = (TrackingActions) cmdArgs.get("action");
    Object          target  = cmdArgs.get("target");
    double[]        coords  = (double[]) cmdArgs.get("coords");

    if (args.length == 2 && args[1].equals("help")) {
      return help((Player) sender, tracker);
    }

    switch (tracker) {
      case COORDS:
        return performCmdTrackCoords((Player) sender, action, target, coords, args);

      case PLAYER:
        return performCmdTrackPlayer((Player) sender, action, target, coords, args);

      case POSITION:
        return performCmdTrackPosition((Player) sender, action, target, coords, args);

      default:
        return false;
    }
  }

  // ----------------------------------------------------------------------------------------------
  // TabCompleter methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player) || args.length < 1 || args.length > 3) return null;

    HashMap<String, Object> cmdArgs = getCommandArguments((Player) sender, args);

    if (args.length == 1) return getTrackersList((Player) sender, args[0]);

    TrackerTypes tracker = (TrackerTypes) cmdArgs.get("tracker");

    if (tracker == null) return null;

    if (args.length == 2) return getActionsList((Player) sender, tracker, args[1]);

    if (args.length > 2 && args[0].equals("help")) return null;

    TrackingActions action = (TrackingActions) cmdArgs.get("action");

    if (action == null || args.length > 3 || action.equals(TrackingActions.ADD)) return null;

    switch (tracker) {
      case COORDS:
        return getFilteredList(plugin.datas.getPlayerRegisteredCoordsList((Player) sender), args[2]);

      case PLAYER:
        return getPlayersList((Player) sender, args[2]);

      case POSITION:
        return plugin.trackers.getPositionTrackersList(args[2]);
    }

    return null;
  }

  // ----------------------------------------------------------------------------------------------
  // Public methods
  // ----------------------------------------------------------------------------------------------

  public boolean help(Player player, TrackerTypes tracker) {
    if (!player.hasPermission("scompass.help") || !player.hasPermission("scompass.track." + tracker)) return false;

    List<String> list = new ArrayList<String>();

    list.add("separator");
    list.add("header");
    list.add("separator");
    list.add(tracker + ".noargs");

    for (TrackingActions action : getActionsAvailable(player, tracker)) list.add(tracker + "." + action);

    list.add("separator");

    for (String key : list) {
      String message = plugin.locale.getString("commands.sctrack.help." + key);

      for (TrackerTypes type : TrackerTypes.values())
        message = message.replace("{" + type + "}",
            plugin.config.getString("tracker_settings.trackers." + type + ".name"));

      for (TrackingActions action : TrackingActions.values())
        message = message.replace("{" + action + "}", plugin.config.getString("tracker_settings.actions." + action));

      player.sendMessage(plugin.formatMessage(message.replace("{prefix}", plugin.locale.getString("prefix"))));
    }

    return true;
  }

  public boolean performCmdTrackCoords(Player player, TrackingActions action, Object target, double[] coords,
      String[] args) {
    if (action == null) {
      List<String> list = plugin.datas.getPlayerRegisteredCoordsList(player);

      if (list.isEmpty())
        plugin.sendMessage(player, "commands.sctrack.COORDS.list_empty");
      else
        plugin.sendMessage(player, "commands.sctrack.COORDS.list", ImmutableMap.of("list", String.join(", ", list)));
    }
    else
      switch (action) {
        case ADD:
          player.sendMessage(plugin.prepareMessage(
              (target == null && plugin.datas.setPlayerRegisteredCoords(player, args[2], coords))
                  ? "commands.sctrack.COORDS.ADD"
                  : "target_exists",
              ImmutableMap.of("target", args[2])));
          break;

        case DEL:
          player.sendMessage(plugin.prepareMessage(
              (target != null && plugin.datas.removePlayerRegisteredCoords(player, args[2]))
                  ? "commands.sctrack.COORDS.DEL"
                  : "target_not_found",
              ImmutableMap.of("target", args[2])));
          break;

        case START:
          plugin.trackers.activateTracker(TrackerTypes.COORDS, player, args[2]);
          plugin.sendMessage(player, "commands.sctrack.COORDS.START", ImmutableMap.of("target", args[2]));
          break;

        case STOP:
          plugin.trackers.disableTracker(TrackerTypes.COORDS, player, args[2]);
          plugin.sendMessage(player, "commands.sctrack.COORDS.STOP", ImmutableMap.of("target", args[2]));
          break;

        default:
          return false;
      }

    return true;
  }

  public boolean performCmdTrackPlayer(Player player, TrackingActions action, Object target, double[] coords,
      String[] args) {
    if (target == null) {
      List<String> list = plugin.trackers.getPlayerTrackers(player);

      if (list.isEmpty())
        plugin.sendMessage(player, "commands.sctrack.PLAYER.list_empty");
      else
        plugin.sendMessage(player, "commands.sctrack.PLAYER.list", ImmutableMap.of("list", String.join(", ", list)));
    }
    else
      switch (action) {
        case ASK:
          boolean online = ((Player) target).isOnline();

          if (online) {
            plugin.trackers.sendPlayerTrackerRequest(player, (Player) target);
            plugin.sendMessage(player, "commands.sctrack.PLAYER.ASK", ImmutableMap.of("target", args[2]));
          }
          else
            plugin.sendMessage(player, "target_not_found", ImmutableMap.of("target", args[2]));
          break;

        case START:
          plugin.trackers.activateTracker(TrackerTypes.PLAYER, player, args[2]);
          plugin.sendMessage(player, "commands.sctrack.PLAYER.START", ImmutableMap.of("target", args[2]));
          break;

        case STOP:
          plugin.trackers.disableTracker(TrackerTypes.PLAYER, player, args[2]);
          plugin.sendMessage(player, "commands.sctrack.PLAYER.STOP", ImmutableMap.of("target", args[2]));
          break;

        default:
          return false;
      }

    return true;
  }

  public boolean performCmdTrackPosition(Player player, TrackingActions action, Object target, double[] coords,
      String[] args) {
    if (action == null) {
      List<String> list = plugin.trackers.getPositionTrackersList(null);

      if (list.isEmpty())
        plugin.sendMessage(player, "commands.sctrack.POSITION.list_empty");
      else
        plugin.sendMessage(player, "commands.sctrack.POSITION.list", ImmutableMap.of("list", String.join(", ", list)));
    }
    else
      switch (action) {
        case ADD:
          player.sendMessage(plugin.prepareMessage(
              (target == null && plugin.trackers.setPositionTracker(player, args[2], coords))
                  ? "commands.sctrack.POSITION.ADD"
                  : "target_exists",
              ImmutableMap.of("target", args[2])));
          break;

        case DEL:
          player.sendMessage(plugin.prepareMessage(
              (target != null && plugin.trackers.removePositionTracker(player, args[2]))
                  ? "commands.sctrack.POSITION.DEL"
                  : "target_not_found",
              ImmutableMap.of("target", args[2])));
          break;

        case START:
          plugin.trackers.activateTracker(TrackerTypes.POSITION, player, args[2]);
          plugin.sendMessage(player, "commands.sctrack.POSITION.START", ImmutableMap.of("target", args[2]));
          break;

        case STOP:
          plugin.trackers.disableTracker(TrackerTypes.POSITION, player, args[2]);
          plugin.sendMessage(player, "commands.sctrack.POSITION.STOP", ImmutableMap.of("target", args[2]));
          break;

        default:
          return false;
      }

    return true;
  }

  // ----------------------------------------------------------------------------------------------
  // Actions methods
  // ----------------------------------------------------------------------------------------------

  private List<TrackingActions> getActionsAvailable(Player player, TrackerTypes tracker) {
    if (!player.hasPermission("scompass.track." + tracker)) return null;

    List<TrackingActions> list = new ArrayList<TrackingActions>();

    switch (tracker) {
      case COORDS:
        list.add(TrackingActions.ADD);
        list.add(TrackingActions.DEL);
        list.add(TrackingActions.START);
        list.add(TrackingActions.STOP);
        break;

      case PLAYER:
        if (player.hasPermission("scompass.track.PLAYER.request"))
          list.add(TrackingActions.ASK);

        if (player.hasPermission("scompass.track.PLAYER.silently"))
          list.add(TrackingActions.START);

        list.add(TrackingActions.STOP);
        break;

      case POSITION:
        if (player.hasPermission("scompass.track.POSITION.manage")) {
          list.add(TrackingActions.ADD);
          list.add(TrackingActions.DEL);
        }

        list.add(TrackingActions.START);
        list.add(TrackingActions.STOP);
        break;
    }

    return list;
  }

  private TrackingActions getActionByName(String name) {
    for (TrackingActions action : TrackingActions.values())
      if (name.equalsIgnoreCase(getActionName(action))) return action;

    return null;
  }

  private String getActionName(TrackingActions action) {
    return plugin.config.getString("tracker_settings.actions." + action);
  }

  private List<String> getActionsList(Player player, TrackerTypes tracker, String startWith) {
    List<String> list = new ArrayList<String>();

    for (TrackingActions action : getActionsAvailable(player, tracker)) {
      String name = getActionName(action);

      if (name.toLowerCase().startsWith(startWith)) list.add(name);
    }

    if (player.hasPermission("scompass.help")) list.add("help");

    return list;
  }

  // ----------------------------------------------------------------------------------------------
  // Utils methods
  // ----------------------------------------------------------------------------------------------

  private HashMap<String, Object> getCommandArguments(Player player, String[] args) {
    HashMap<String, Object> cmdArgs = new HashMap<String, Object>();

    if (args.length == 0) return cmdArgs;

    TrackerTypes tracker = plugin.trackers.getTrackerTypeByName(args[0]);

    if (!plugin.trackers.getAvailableTrackerTypes(player).contains(tracker)) return cmdArgs;

    cmdArgs.put("tracker", tracker);

    if (args.length == 1) return cmdArgs;

    TrackingActions action = getActionByName(args[1]);

    if (!getActionsAvailable(player, tracker).contains(action)) return cmdArgs;

    cmdArgs.put("action", action);

    if (args.length == 2) return cmdArgs;

    cmdArgs.put("target", plugin.trackers.getTrackerTargetByName(player, tracker, args[2]));

    if (args.length == 5
        && (player.hasPermission("scompass.track.COORDS") || player.hasPermission("scompass.track.POSITION.manage")))
      try {
        double[] coords = { Double.parseDouble(args[3]), Double.parseDouble(args[4]) };
        cmdArgs.put("coords", coords);
      }
      catch (Exception e) {}

    return cmdArgs;
  }

  private List<String> getFilteredList(List<?> inputList, String startWith) {
    List<String> list = new ArrayList<String>();

    for (Object candidate : inputList) {
      if (candidate.toString().toLowerCase().startsWith(startWith.toLowerCase()))
        list.add(candidate.toString());
    }

    return list;
  }

  private List<String> getPlayersList(Player player, String startWith) {
    if (!player.hasPermission("scompass.track.PLAYER")) return null;

    List<String> list = new ArrayList<String>();

    for (Player target : plugin.getServer().getOnlinePlayers())
      if (target.getName().toLowerCase().startsWith(startWith.toLowerCase()))
        list.add(target.getName());

    return list;
  }

  private List<String> getTrackersList(Player player, String startWith) {
    List<String> list = new ArrayList<String>();

    for (TrackerTypes tracker : plugin.trackers.getAvailableTrackerTypes(player)) {
      String name = plugin.trackers.getTrackerTypeCustomName(tracker);

      if (name.toLowerCase().startsWith(startWith)) list.add(name);
    }

    return list;
  }
}
