package me.arboriginal.SimpleCompass.trackers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public abstract class AbstractTracker {
  SimpleCompass sc;

  public enum TrackingActions {
    ADD, ASK, DEL, HELP, START, STOP,
  }
  
  public enum TargetSelector {
    ACTIVE, AVAILABLE, NEW, NEWCOORDS, NONE,
  }

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public AbstractTracker(SimpleCompass plugin) {
    sc = plugin;
  }

  // ----------------------------------------------------------------------------------------------
  // Actions methods
  // ----------------------------------------------------------------------------------------------
  
  public TrackingActions getActionByName(String name) {
    for (TrackingActions action : TrackingActions.values())
      if (name.equalsIgnoreCase(getActionName(action))) return action;

    return null;
  }

  public String getActionName(TrackingActions action) {
    return sc.locale.getString("actions." + action);
  }

  public List<TrackingActions> getActionsAvailable(Player player, boolean keepUnavailable) {
    List<TrackingActions> list = new ArrayList<TrackingActions>();

    if (player.hasPermission("scompass.help")) list.add(TrackingActions.HELP);

    return list;
  }

  public boolean limitReached(Player player, TrackingActions action, boolean showError, Integer current) {
    if (!sc.config.contains("tracker_settings.trackers." + trackerID() + ".limits." + action)) return false;

    int limit = sc.config.getInt("tracker_settings.trackers." + trackerID() + ".limits." + action), count;

    if (current == null) {
      UUID uid = player.getUniqueId();

      count = sc.targets.activeTargets.get(trackerID()).containsKey(uid)
          ? sc.targets.activeTargets.get(trackerID()).get(uid).size()
          : 0;
    }
    else
      count = current;

    if (count < limit) return false;

    if (showError) sc.sendMessage(player, "commands.sctrack.limits." + action,
        ImmutableMap.of("limit", "" + limit, "tracker", trackerName()));

    return true;
  }

  public TargetSelector requireTarget(TrackingActions action) {
    switch (action) {
      case ADD:
        return TargetSelector.NEW;
        
      case ASK:
      case DEL:
      case START:
        return TargetSelector.AVAILABLE;
        
      case STOP:
        return TargetSelector.ACTIVE;
        
      default: 
        return TargetSelector.NONE;
    }
  }
  
  // ----------------------------------------------------------------------------------------------
  // Tracker methods
  // ----------------------------------------------------------------------------------------------

  public abstract String trackerID();

  public String trackerName() {
    return sc.locale.getString("trackers." + trackerID());
  }

  // ----------------------------------------------------------------------------------------------
  // Targets methods
  // ----------------------------------------------------------------------------------------------

  public boolean activate(Player player, String name, boolean showError) {
    if (limitReached(player, TrackingActions.START, showError, null)) return false;

    sc.targets.activateTarget(player, trackerID(), name);
    return true;
  }

  public List<String> activeTargets(Player player, String startWith) {
    List<String> list = new ArrayList<String>();

    for (String candidate : sc.datas.activeTargetsList(player, trackerID()))
      if (startWith.isEmpty() || candidate.toLowerCase().startsWith(startWith.toLowerCase())) list.add(candidate);

    return list;
  }

  public List<String> availableTargets(Player player, String startWith) {
    List<String> list = new ArrayList<String>();
    String       root = key(player);

    if (datas().contains(root))
      ((MemorySection) datas().get(root)).getKeys(false).forEach(candidate -> {
        if (startWith.isEmpty() || candidate.toLowerCase().startsWith(startWith.toLowerCase())) list.add(candidate);
      });

    return list;
  }

  public boolean del(Player player, String name) {
    String key = key(player, name);

    if (datas().contains(key)) {
      disable(player, name);
      return save(key, null);
    }

    return false;
  }

  public void disable(Player player, String name) {
    sc.targets.disableTarget(player, trackerID(), name);
  }

  public double[] get(Player player, String name) {
    String key = key(player, name);

    if (datas().contains(key + ".x") && datas().contains(key + ".z"))
      return new double[] { datas().getDouble(key + ".x"), datas().getDouble(key + ".z") };

    return null;
  }

  public List<String> list(Player player, TrackingActions action, String startWith) {
    if (action == null) return availableTargets(player, startWith);

    List<String> list = new ArrayList<String>();

    switch (action) {
      case ADD:
        break;

      case ASK:
      case DEL:
      case START:
        list.addAll(availableTargets(player, startWith));
        break;

      default:
        list.addAll(activeTargets(player, startWith));
        break;
    }

    return list;
  }

  public boolean set(Player player, String name, double[] coords) {
    String key = key(player, name);

    if (datas().contains(key)) return false;

    String root    = key(player);
    int    current = datas().contains(root)
        ? datas().getConfigurationSection(root).getKeys(false).size()
        : 0;

    if (limitReached(player, TrackingActions.ADD, true, current)) return false;

    boolean success = (save(key + ".x", coords[0]) && save(key + ".z", coords[1]));
    if (success) return true;

    save(key, null);
    return false;
  }

  // ----------------------------------------------------------------------------------------------
  // Command methods
  // ----------------------------------------------------------------------------------------------

  public List<String> commandSuggestions(Player player, String[] args, HashMap<String, Object> parsed) {
    List<String> list = new ArrayList<>();

    if (args.length < 1 || args.length > 3) return list;

    switch (args.length) {
      case 2:
        for (TrackingActions action : getActionsAvailable(player, false)) {
          String name = getActionName(action);

          if (name.toLowerCase().startsWith(args[1].toLowerCase())) list.add(name);
        }
        break;

      case 3:
        list.addAll(list(player, (TrackingActions) parsed.get("action"), args[2]));
        break;
    }

    return list;
  }

  public String help(Player player, String command) {
    List<String> list = new ArrayList<String>();
    String       help = "";

    list.add("separator");
    list.add("header");
    list.add("separator");
    list.add(trackerID() + ".noargs");
    for (TrackingActions action : getActionsAvailable(player, true))
      if (!action.equals(TrackingActions.HELP)) list.add(trackerID() + "." + action);
    list.add("separator");

    for (String key : list) {
      String message = sc.locale.getString("commands.sctrack.help." + key)
          .replace("{command}", command).replace("{" + trackerID() + "}", trackerName()) + "\n";
      if (key.equals("header")) message = message.replace("{tracker}", trackerName());
      for (TrackingActions action : TrackingActions.values())
        message = message.replace("{" + action + "}", sc.locale.getString("actions." + action));

      help += sc.formatMessage(message.replace("{prefix}", sc.locale.getString("prefix")));
    }

    return help;
  }

  public void parseArguments(Player player, String[] args, HashMap<String, Object> parsed) {
    TrackingActions action = getActionByName(args[1]);

    if (action == null || !getActionsAvailable(player, false).contains(action)) return;

    parsed.put("action", action);

    if (args.length == 2) return;
    if (get(player, args[2]) != null) parsed.put("target", args[2]);
  }

  public abstract boolean perform(Player player, String command, TrackingActions action, String target, String[] args);

  // ----------------------------------------------------------------------------------------------
  // Storage methods
  // ----------------------------------------------------------------------------------------------

  public MemorySection datas() {
    return sc.datas.users;
  }

  public String key(Player player) {
    return key(player, null);
  }

  public String key(Player player, String name) {
    return sc.datas.getKey(player, trackerID() + (name == null ? "" : "." + name.toLowerCase()));
  }

  public boolean save(String key, Object value) {
    datas().set(key, value);

    return sc.datas.saveUserDatas();
  }
}
