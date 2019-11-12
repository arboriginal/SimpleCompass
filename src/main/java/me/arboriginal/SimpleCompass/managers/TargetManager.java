package me.arboriginal.SimpleCompass.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class TargetManager {
    private SimpleCompass sc;
    public String[]       trackersPriority;

    public HashMap<String, HashMap<UUID, List<String>>> activeTargets;

    // Constructor methods ---------------------------------------------------------------------------------------------

    public TargetManager(SimpleCompass plugin) {
        sc            = plugin;
        activeTargets = new HashMap<String, HashMap<UUID, List<String>>>();

        for (String trackerID : sc.trackers.keySet()) activeTargets.put(trackerID, new HashMap<UUID, List<String>>());

        trackersPriority = new String[sc.trackers.size()];
        if (sc.trackers.size() == 0) return;

        List<String> priority = sc.config.getStringList("trackers_priorities");
        for (int i = 0; i < priority.size(); i++) trackersPriority[i] = priority.get(i);
    }

    // Trackers methods ------------------------------------------------------------------------------------------------

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

    // Targets methods -------------------------------------------------------------------------------------------------

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

    public HashMap<String, HashMap<String, ArrayList<double[]>>> getTargetsCoords(Player player) {
        HashMap<String, HashMap<String, ArrayList<double[]>>> list // @formatter:off
            = new HashMap<String, HashMap<String, ArrayList<double[]>>>();
        // @formatter:on
        list.put("on", new HashMap<String, ArrayList<double[]>>());
        list.put("off", new HashMap<String, ArrayList<double[]>>());

        HashMap<AbstractTracker, ArrayList<String>> stop = new HashMap<AbstractTracker, ArrayList<String>>();

        UUID uid = player.getUniqueId();

        List<String> invalids = new ArrayList<String>();

        for (String trackerID : sc.trackers.keySet()) {
            AbstractTracker tracker = sc.trackers.get(trackerID);
            if (tracker == null) continue;

            HashMap<String, double[]> sublistOn = new HashMap<String, double[]>();

            if (activeTargets.get(trackerID).containsKey(uid)) {
                ArrayList<String> closest = new ArrayList<String>();

                Iterator<String> it = activeTargets.get(trackerID).get(uid).iterator();
                while (it.hasNext()) {
                    String   name   = it.next();
                    double[] coords = tracker.get(player, name);
                    if (coords == null) invalids.add(trackerID + ":" + name);
                    if (coords == null || tracker.playerIsClose(player, coords)) closest.add(name);
                    else sublistOn.put(name, coords);
                }

                if (!sublistOn.isEmpty()) list.get("on").put(trackerID, new ArrayList<double[]>(sublistOn.values()));
                if (!closest.isEmpty()) stop.put(tracker, closest);
            }

            if (tracker.settings.getBoolean("settings.inactive_target", false)) {
                ArrayList<double[]> sublistOff = new ArrayList<double[]>();

                for (String name : tracker.availableTargets(player, "")) {
                    if (sublistOn.containsKey(name)) continue;
                    double[] coords = tracker.get(player, name);
                    if (coords != null) sublistOff.add(coords);
                }

                if (!sublistOff.isEmpty()) list.get("off").put(trackerID, sublistOff);
            }
        }

        if (!stop.isEmpty()) {
            stop.forEach((tracker, stopped) -> {
                stopped.forEach(name -> {
                    tracker.disable(player, name);
                    if (!invalids.contains(tracker.trackerID() + ":" + name)) tracker.sendMessage(player,
                            "target_auto_disabled", ImmutableMap.of("tracker", tracker.trackerName(), "target", name));
                });
            });
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
