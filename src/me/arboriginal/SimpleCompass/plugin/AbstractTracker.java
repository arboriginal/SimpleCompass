package me.arboriginal.SimpleCompass.plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;

public abstract class AbstractTracker {
  protected SimpleCompass sc;
  protected File          sf;
  protected URL           res;

  public enum TrackingActions {
    ADD, ACCEPT, ASK, DEL, DENY, HELP, START, STOP,
  }

  public enum TargetSelector {
    ACTIVE, AVAILABLE, NEW, NEWCOORDS, NONE,
  }

  public FileConfiguration settings;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public AbstractTracker(SimpleCompass plugin) {
    sc  = plugin;
    sf  = new File(sc.getDataFolder(), "trackers/" + getClass().getSimpleName() + ".yml");
    res = getClass().getResource("/settings.yml");
  }

  // ----------------------------------------------------------------------------------------------
  // Tracker methods
  // ----------------------------------------------------------------------------------------------

  public abstract String trackerID();

  public String github() {
    return null;
  }

  public String trackerName() {
    return settings.getString("locales." + sc.config.getString("language") + ".name");
  }

  public String version() {
    return "?";
  }

  // ----------------------------------------------------------------------------------------------
  // Initialization and update methods
  // ----------------------------------------------------------------------------------------------

  /**
   * At this state, config has not been read from user file,
   * so DO NOT USE sc.config here, and DO NOT call methods which use this.
   */
  public boolean init() {
    if (res == null) {
      sc.getLogger().warning("settings.yml missing in " + sf.getAbsolutePath());
      return false;
    }

    settings = YamlConfiguration.loadConfiguration(sf);
    settings.options().copyDefaults(true);

    InputStream is;

    try {
      is = res.openStream();
    }
    catch (Exception e) {
      sc.getLogger().warning("Can't write default settings to " + sf.getAbsolutePath());
      return false;
    }

    settings.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(is)));

    try {
      settings.save(sf);
    }
    catch (Exception e) {
      sc.getLogger().severe("Can't write to " + sf.getAbsolutePath());
      return false;
    }

    return true;
  }

  public void checkUpdate(CommandSender sender) {
    if (!settings.getBoolean("settings.check_update", true)) return;

    String github = github();

    if (github == null) {
      sendMessage(sender, "tracker_check_update_available",
          ImmutableMap.of("tracker", trackerName(), "version", "new", "current", version()));
      return;
    }

    String version = sc.githubVersion(github);

    if (version == null)
      sendMessage(sender, "tracker_check_update_failed", ImmutableMap.of("tracker", trackerName()));
    else {
      String current = version();
      if (!version.equals(current))
        sendMessage(sender, "tracker_check_update_available",
            ImmutableMap.of("tracker", trackerName(), "version", version, "current", current));
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Utils methods
  // ----------------------------------------------------------------------------------------------

  public void sendMessage(CommandSender sender, String key) {
    sendMessage(sender, key, null);
  }

  public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
    if (key.isEmpty()) return;
    String message = prepareMessage(key, placeholders);
    if (!message.isEmpty()) sender.sendMessage(message);
  }

  public String prepareMessage(String key) {
    return prepareMessage(key, null);
  }

  public String prepareMessage(String key, Map<String, String> placeholders) {
    String message = settings.getString("locales." + sc.config.getString("language") + "." + key);
    if (message == null) message = sc.locale.getString(key);
    if (message == null) return "";

    if (placeholders != null) {
      for (Iterator<String> i = placeholders.keySet().iterator(); i.hasNext();) {
        String placeholder = i.next();
        message = message.replace("{" + placeholder + "}", placeholders.get(placeholder));
      }
    }

    return sc.formatMessage(message.replace("{prefix}", sc.locale.getString("prefix")));
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
    if (!settings.contains("settings.limits." + action)) return false;
    int limit = settings.getInt("settings.limits." + action), count;

    if (current == null) {
      UUID uid = player.getUniqueId();

      count = sc.targets.activeTargets.get(trackerID()).containsKey(uid)
          ? sc.targets.activeTargets.get(trackerID()).get(uid).size()
          : 0;
    }
    else
      count = current;

    if (count < limit) return false;

    if (showError) sendMessage(player, "commands.sctrack.limits." + action,
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

  public List<String> autoloadTargets(Player player, String startWith) {
    List<String> list = new ArrayList<String>();

    if (settings.getBoolean("settings.autoload_target", false)) {
      String perm = "scompass.track.auto." + trackerID() + ".";
      for (String name : availableTargets(player, startWith))
        if (player.hasPermission(perm + "*") || player.hasPermission(perm + name)) list.add(name);
    }

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

  public double[] getCoords(Player player, String[] args) {
    double[] coords = null;

    if (args.length == 5)
      try {
        coords = new double[] { Double.parseDouble(args[3]), Double.parseDouble(args[4]) };
      }
      catch (Exception e) {}

    if (coords == null) coords = new double[] { player.getLocation().getX(), player.getLocation().getZ() };

    return coords;
  }

  public List<String> list(Player player, TrackingActions action, String startWith) {
    if (action == null) return availableTargets(player, startWith);

    List<String> list = new ArrayList<String>();

    switch (action) {
      case ACCEPT:
      case ADD:
      case DENY:
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

  public List<String> listFiltered(Player player, List<String> list) {
    if (player.hasPermission("scompass.track." + trackerID() + ".defined.*")) return list;

    List<String> filtered = new ArrayList<String>();
    for (String name : list)
      if (player.hasPermission("scompass.track." + trackerID() + ".defined." + name)) filtered.add(name);

    return filtered;
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

  public boolean playerIsClose(Player player, double[] coords) {
    int dist = settings.getInt("settings.auto_disabled", 0);
    return (dist > 0 && player.getLocation().distance(
        new Location(player.getWorld(), coords[0], player.getLocation().getY(), coords[1])) < dist);
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
    String sep  = prepareMessage("commands.sctrack.help.separator") + "\n";
    String help = sep + prepareMessage("commands.sctrack.help.header", ImmutableMap.of("tracker", trackerName())) + sep;

    List<String> list = new ArrayList<String>();
    list.add("noargs");

    HashMap<String, String> placeholders = new HashMap<String, String>();
    placeholders.put("command", command);
    placeholders.put("tracker", trackerName());

    for (TrackingActions action : getActionsAvailable(player, true)) if (!action.equals(TrackingActions.HELP)) {
      list.add(action.toString());
      placeholders.put(action.toString(), sc.locale.getString("actions." + action));
    }

    for (String key : list) help += prepareMessage("help." + key, placeholders) + "\n";
    return help + sep;
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
    return sc.datas.getKey(player, trackerID() + (name == null ? "" : "." + name));
  }

  public boolean save(String key, Object value) {
    datas().set(key, value);
    return sc.datas.saveUserDatas();
  }
}
