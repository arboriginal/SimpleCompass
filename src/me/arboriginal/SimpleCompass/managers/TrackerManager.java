package me.arboriginal.SimpleCompass.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.CacheUtil;

public class TrackerManager {
  private SimpleCompass plugin;

  private HashMap<UUID, HashMap<UUID, Long>>                 requests;
  private HashMap<TrackerTypes, HashMap<UUID, List<String>>> trackers;

  public enum TrackerTypes {
    COORDS, PLAYER, POSITION,
  }

  public TrackerTypes[] trackersPriority;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public TrackerManager(SimpleCompass main) {
    plugin   = main;
    requests = new HashMap<UUID, HashMap<UUID, Long>>();
    trackers = new HashMap<TrackerTypes, HashMap<UUID, List<String>>>();

    for (TrackerTypes type : TrackerTypes.values())
      trackers.put(type, new HashMap<UUID, List<String>>());

    trackersPriority = new TrackerTypes[TrackerTypes.values().length];
    List<?> priority = plugin.config.getList("tracker_settings.priority");

    for (int i = 0; i < priority.size(); i++)
      trackersPriority[i] = TrackerTypes.valueOf((String) priority.get(i));
  }

  // ----------------------------------------------------------------------------------------------
  // Trackers types methods
  // ----------------------------------------------------------------------------------------------

  public List<TrackerTypes> getAvailableTrackerTypes(Player player) {
    List<TrackerTypes> list = new ArrayList<TrackerTypes>();

    for (TrackerTypes tracker : TrackerTypes.values()) {
      if (player.hasPermission("scompass.track." + tracker))
        list.add(tracker);
    }

    return list;
  }

  public TrackerTypes getTrackerTypeByName(String name) {
    for (TrackerTypes tracker : TrackerTypes.values())
      if (name.equals(getTrackerTypeCustomName(tracker))) return tracker;

    return null;
  }

  public String getTrackerTypeCustomName(TrackerTypes tracker) {
    return plugin.config.getString("tracker_settings.trackers." + tracker + ".name");
  }

  // ----------------------------------------------------------------------------------------------
  // Trackers methods
  // ----------------------------------------------------------------------------------------------

  public void activateTracker(TrackerTypes type, Player player, String name) {
    UUID uid = player.getUniqueId();

    if (!trackers.get(type).containsKey(uid)) trackers.get(type).put(uid, new ArrayList<String>());
    if (trackers.get(type).get(uid).contains(name)) return;

    trackers.get(type).get(uid).add(name);
    plugin.datas.playerAtivatedTrackerAdd(player, type, name);
  }

  public void disableTracker(TrackerTypes type, Player player, String name) {
    UUID uid = player.getUniqueId();

    if (!trackers.get(type).containsKey(uid)) return;
    if (!trackers.get(type).get(uid).contains(name)) return;

    trackers.get(type).get(uid).remove(name);
    plugin.datas.playerAtivatedTrackerDel(player, type, name);
  }

  public HashMap<TrackerTypes, ArrayList<double[]>> getActivatedTrackersCoords(Player player) {
    HashMap<TrackerTypes, ArrayList<double[]>> list = new HashMap<TrackerTypes, ArrayList<double[]>>();

    UUID uid = player.getUniqueId();

    for (TrackerTypes type : TrackerTypes.values()) {
      if (trackers.get(type).containsKey(uid)) {
        ArrayList<double[]> sublist = new ArrayList<double[]>();

        for (String name : trackers.get(type).get(uid)) {
          Object target = getTrackerTargetByName(player, type, name);

          if (target instanceof Player && ((Player) target).isOnline()) {
            double[] coords = { ((Player) target).getLocation().getX(), ((Player) target).getLocation().getZ() };
            sublist.add(coords);
          }
          else if (target instanceof double[]) sublist.add((double[]) target);
        }

        if (!sublist.isEmpty()) list.put(type, sublist);
      }
    }

    return list;
  }

  public Object getTrackerTargetByName(Player player, TrackerTypes tracker, String name) {
    switch (tracker) {
      case COORDS:
        return plugin.datas.getPlayerRegisteredCoords(player, name);

      case PLAYER:
        if (player.hasPermission("scompass.track.PLAYER"))
          for (Player target : plugin.getServer().getOnlinePlayers())
            if (target.getName().equalsIgnoreCase(name)) return target;

        return null;

      case POSITION:
        return getPositionTrackerByName(player, name);
    }

    return null;
  }

  public boolean loadTrackers() {
    boolean hasLoadedTrackers = false;

    for (Player player : plugin.getServer().getOnlinePlayers())
      if (loadTrackers(player)) hasLoadedTrackers = true;

    return hasLoadedTrackers;
  }

  public boolean loadTrackers(Player player) {
    boolean hasLoadedTrackers = false;

    for (TrackerTypes type : TrackerTypes.values()) {
      List<String> trackers = plugin.datas.playerAtivatedTrackersList(player, type);

      if (!trackers.isEmpty()) hasLoadedTrackers = true;

      for (String name : trackers)
        activateTracker(type, player, name);
    }

    return hasLoadedTrackers;
  }

  public void unloadTrackers(Player player) {
    UUID uid = player.getUniqueId();

    for (TrackerTypes type : TrackerTypes.values()) unloadTrackers(uid, type);

    cleanPlayerTrackerRequests(player, true);
  }

  public void unloadTrackers(UUID uid, TrackerTypes type) {
    if (trackers.get(type).containsKey(uid)) trackers.get(type).remove(uid);
  }

  // ----------------------------------------------------------------------------------------------
  // Player tracking methods
  // ----------------------------------------------------------------------------------------------

  public void cleanPlayerTrackerRequests(Player player) {
    cleanPlayerTrackerRequests(player, false);
  }

  public void cleanPlayerTrackerRequests(Player player, boolean delete) {
    UUID uid = player.getUniqueId();

    if (!requests.containsKey(uid)) return;
    if (delete)
      requests.remove(uid);
    else {
      requests.get(uid).forEach((seeker, until) -> {
        if (CacheUtil.now() > until) requests.get(uid).remove(seeker);
      });
    }
  }

  public Set<UUID> getPlayerTrackerRequests(Player player) {
    UUID uid = player.getUniqueId();

    return requests.containsKey(uid) ? requests.get(uid).keySet() : null;
  }

  public long getPlayerTrackerRequest(Player player, Player seeker) {
    UUID pUid = player.getUniqueId(), sUid = seeker.getUniqueId();

    cleanPlayerTrackerRequests(player);

    if (requests.containsKey(pUid) && requests.get(pUid).containsKey(sUid)) {
      return requests.get(pUid).get(sUid);
    }

    return 0;
  }

  public List<String> getPlayerTrackers(Player player) {
    List<String> list = new ArrayList<String>();
    UUID         uid  = player.getUniqueId();

    if (trackers.get(TrackerTypes.PLAYER).containsKey(uid)) {
      list.addAll(trackers.get(TrackerTypes.PLAYER).get(uid));
    }

    return list;
  }

  public void removePlayerTrackerRequest(Player player, Player seeker) {
    UUID pUid = player.getUniqueId(), sUid = seeker.getUniqueId();

    if (requests.containsKey(pUid) && requests.get(pUid).containsKey(sUid)) requests.get(pUid).remove(sUid);
  }

  public boolean respondPlayerTrackerRequest(Player player, String seekerName, boolean accept) {
    Player seeker = plugin.getServer().getPlayer(seekerName);

    if (seeker == null || !seeker.isOnline()) return true;

    Long request = getPlayerTrackerRequest(player, seeker);

    if (request == 0) {
      plugin.sendMessage(player, "request_expired", ImmutableMap.of("player", seekerName));
      return true;
    }

    removePlayerTrackerRequest(player, seeker);

    ImmutableMap<String, String> placeholders = ImmutableMap.of("player", seekerName, "target", player.getName());

    if (accept) {
      activateTracker(TrackerTypes.PLAYER, seeker, player.getName());

      plugin.sendMessage(player, "commands.sctrack.PLAYER.request.accepted.target", placeholders);
      plugin.sendMessage(seeker, "commands.sctrack.PLAYER.request.accepted.player", placeholders);
    }
    else {
      plugin.sendMessage(player, "commands.sctrack.PLAYER.request.refused.target", placeholders);
      plugin.sendMessage(seeker, "commands.sctrack.PLAYER.request.refused.player", placeholders);
    }

    return true;
  }

  public void sendPlayerTrackerRequest(Player player, Player target) {
    Map<String, Map<String, String>> commands = new HashMap<String, Map<String, String>>();

    for (String action : ImmutableList.of("accept", "deny")) {
      commands.put("{" + action + "}", ImmutableMap.of(
          "text", plugin.prepareMessage("commands.sctrack.PLAYER.request." + action),
          "click", "/sct" + action + " " + target.getName(),
          "hover", plugin.prepareMessage("commands.sctrack.PLAYER.request." + action + "_hover",
              ImmutableMap.of("player", player.getName()))));
    }

    setPlayerTrackerRequest(target, player);
    player.spigot().sendMessage(plugin.createClickableMessage(plugin.prepareMessage(
        "commands.sctrack.PLAYER.request.message", ImmutableMap.of("player", player.getName())), commands));
  }

  public void setPlayerTrackerRequest(Player player, Player seeker) {
    UUID pUid = player.getUniqueId(), sUid = seeker.getUniqueId();

    if (!requests.containsKey(pUid))
      requests.put(pUid, new HashMap<UUID, Long>());
    else
      cleanPlayerTrackerRequests(player);

    requests.get(pUid).put(sUid,
        CacheUtil.now() + plugin.config.getInt("tracker_settings.request_duration") * 1000);
  }

  // ----------------------------------------------------------------------------------------------
  // Position tracking methods
  // ----------------------------------------------------------------------------------------------

  public double[] getPositionTrackerByName(Player player, String name) {
    if (plugin.config.contains("positions." + name + ".x") && plugin.config.contains("positions." + name + ".z"))
      return new double[] {
          plugin.config.getDouble("positions." + name + ".x"), plugin.config.getDouble("positions." + name + ".z")
      };

    return null;
  }

  public List<String> getPositionTrackersList(String startWith) {
    List<String> list = new ArrayList<String>();

    if (plugin.config.contains("positions"))
      for (String position : plugin.config.getConfigurationSection("positions").getKeys(false))
        if (startWith == null || position.toLowerCase().startsWith(startWith.toLowerCase())) list.add(position);

    return list;
  }

  public boolean removePositionTracker(Player player, String name) {
    String key = "positions." + name;

    if (!plugin.config.contains(key)) return false;

    plugin.trackers.disableTracker(TrackerTypes.POSITION, player, name);
    plugin.getConfig().set(key, null);
    plugin.saveConfig();

    return true;
  }

  public boolean setPositionTracker(Player player, String name, double[] coords) {
    String key = "positions." + name;

    if (plugin.config.contains(key)) return false;

    if (coords == null) {
      double[] currentPosition = { player.getLocation().getX(), player.getLocation().getZ() };
      coords = currentPosition;
    }

    plugin.getConfig().set(key + ".x", coords[0]);
    plugin.getConfig().set(key + ".z", coords[1]);
    plugin.saveConfig();

    return true;
  }
}
