package me.arboriginal.SimpleCompass.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class TargetManager {
  private SimpleCompass sc;

  public String[] trackersPriority;

  public HashMap<String, HashMap<UUID, List<String>>> activeTargets;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public TargetManager(SimpleCompass plugin) {
    sc            = plugin;
    activeTargets = new HashMap<String, HashMap<UUID, List<String>>>();

    for (String trackerID : sc.trackers.keySet())
      activeTargets.put(trackerID, new HashMap<UUID, List<String>>());

    trackersPriority = new String[sc.trackers.size()];
    if (sc.trackers.size() == 0) return;

    List<String> priority = sc.config.getStringList("trackers_priorities");
    for (int i = 0; i < priority.size(); i++) trackersPriority[i] = priority.get(i);
  }

  // ----------------------------------------------------------------------------------------------
  // Trackers methods
  // ----------------------------------------------------------------------------------------------

  public boolean canUseTracker(Player player, String trackerID) {
    return player.hasPermission("scompass.track.*") || player.hasPermission("scompass.track." + trackerID);
  }

  public List<String> getAvailableTrackers(Player player) {
    List<String> list = new ArrayList<String>();
    for (String trackerID : sc.trackers.keySet()) if (canUseTracker(player, trackerID)) list.add(trackerID);
    return list;
  }

  public AbstractTracker getTrackerByName(String name) {
    for (String trackerID : sc.trackers.keySet()) {
      AbstractTracker tracker = sc.trackers.get(trackerID);

      if (name.toLowerCase().equals(tracker.trackerName().toLowerCase())) return tracker;
    }

    return null;
  }

  public List<String> getTrackersList(Player player, String startWith) {
    List<String> list = new ArrayList<String>();

    for (String trackerID : sc.targets.getAvailableTrackers(player)) {
      String name = sc.trackers.get(trackerID).trackerName();

      if (name.toLowerCase().startsWith(startWith.toLowerCase())) list.add(name);
    }

    return list;
  }

  // ----------------------------------------------------------------------------------------------
  // Targets methods
  // ----------------------------------------------------------------------------------------------

  public void activateTarget(Player player, String type, String name) {
    UUID uid = player.getUniqueId();

    if (!activeTargets.get(type).containsKey(uid)) activeTargets.get(type).put(uid, new ArrayList<String>());
    if (activeTargets.get(type).get(uid).contains(name)) return;

    activeTargets.get(type).get(uid).add(name);
    sc.datas.activeTargetAdd(player, type, name);
  }

  public void disableTarget(Player player, String type, String name) {
    UUID uid = player.getUniqueId();

    if (!activeTargets.get(type).containsKey(uid)) return;
    if (!activeTargets.get(type).get(uid).contains(name)) return;

    activeTargets.get(type).get(uid).remove(name);
    sc.datas.activeTargetDel(player, type, name);
  }

  public HashMap<String, ArrayList<double[]>> getTargetsCoords(Player player) {
    HashMap<String, ArrayList<double[]>> list = new HashMap<String, ArrayList<double[]>>();
    UUID                                 uid  = player.getUniqueId();

    for (String trackerID : sc.trackers.keySet()) {
      if (activeTargets.get(trackerID).containsKey(uid)) {
        AbstractTracker tracker = sc.trackers.get(trackerID);

        if (tracker == null) continue;

        ArrayList<double[]> sublist = new ArrayList<double[]>();

        for (String name : activeTargets.get(trackerID).get(uid)) {
          double[] coords = tracker.get(player, name);

          if (coords != null) sublist.add(coords);
        }

        if (!sublist.isEmpty()) list.put(trackerID, sublist);
      }
    }

    return list;
  }

  public boolean loadTargets() {
    boolean hasLoadedTrackers = false;

    for (Player player : sc.getServer().getOnlinePlayers()) if (loadTargets(player)) hasLoadedTrackers = true;

    return hasLoadedTrackers;
  }

  public boolean loadTargets(Player player) {
    boolean hasLoadedTrackers = false;

    for (String trackerID : sc.trackers.keySet()) {
      List<String> targets = sc.datas.activeTargetsList(player, trackerID);

      if (!targets.isEmpty()) hasLoadedTrackers = true;

      for (String name : targets) activateTarget(player, trackerID, name);
    }

    return hasLoadedTrackers;
  }

  public void unloadTargets(Player player) {
    UUID uid = player.getUniqueId();

    for (String trackerID : sc.trackers.keySet())
      if (activeTargets.get(trackerID).containsKey(uid)) activeTargets.get(trackerID).remove(uid);
  }
}
