package me.arboriginal.SimpleCompass.trackers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.CacheUtil;

public class PlayerTracker extends AbstractTracker implements CommandExecutor, Listener, TabCompleter {
  private HashMap<UUID, HashMap<UUID, Long>> requests;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public PlayerTracker(SimpleCompass plugin) {
    super(plugin);

    requests = new HashMap<UUID, HashMap<UUID, Long>>();

    sc.getCommand("scompass-track-accept").setExecutor(this);
    sc.getCommand("scompass-track-deny").setExecutor(this);
  }

  // ----------------------------------------------------------------------------------------------
  // Listener methods
  // ----------------------------------------------------------------------------------------------

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    cleanPlayerTrackerRequests(event.getPlayer(), true);
  }

  // ----------------------------------------------------------------------------------------------
  // Actions methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<TrackingActions> getActionsAvailable(Player player, boolean keepUnavailable) {
    List<TrackingActions> list = super.getActionsAvailable(player, keepUnavailable);

    if (player.hasPermission("scompass.track." + trackerID() + ".request"))
      list.add(TrackingActions.ASK);

    if (player.hasPermission("scompass.track." + trackerID() + ".silently")
        && !availableTargets(player, "").isEmpty())
      list.add(TrackingActions.START);

    if (!list(player, null, "").isEmpty()) list.add(TrackingActions.STOP);

    return list;
  }

  // ----------------------------------------------------------------------------------------------
  // Tracker methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public String trackerID() {
    return "PLAYER";
  }

  // ----------------------------------------------------------------------------------------------
  // Targets methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> availableTargets(Player player, String startWith) {
    List<String> list = new ArrayList<String>();

    sc.getServer().getOnlinePlayers().forEach(candidate -> {
      String name = candidate.getName();
      if (!name.equals(player.getName()))
        if (startWith.isEmpty() || name.toLowerCase().startsWith(startWith.toLowerCase())) list.add(name);
    });

    return list;
  }

  @Override
  public boolean del(Player player, String name) {
    return false;
  }

  @Override
  public double[] get(Player player, String name) {
    for (Player target : sc.getServer().getOnlinePlayers())
      if (target.getName().equalsIgnoreCase(name))
        return new double[] { target.getLocation().getX(), target.getLocation().getZ() };

    return null;
  }

  @Override
  public List<String> list(Player player, TrackingActions action, String startWith) {
    if (action == null) return activeTargets(player, startWith);

    if (action.equals(TrackingActions.STOP)) {
      List<String> list = new ArrayList<String>();

      for (OfflinePlayer candidate : sc.getServer().getOfflinePlayers()) {
        String name = candidate.getName();
        if (!name.equals(player.getName()))
          if (startWith.isEmpty() || name.toLowerCase().startsWith(startWith.toLowerCase())) list.add(name);
      }

      return list;
    }

    return super.list(player, action, startWith);
  }

  @Override
  public boolean set(Player player, String name, double[] coords) {
    return false;
  }

  // ----------------------------------------------------------------------------------------------
  // Command methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> commandSuggestions(Player player, String[] args, HashMap<String, Object> parsed) {
    if (args.length == 3 && parsed.get("action") != null && !parsed.get("action").equals(TrackingActions.STOP))
      return availableTargets(player, args[2]);

    return super.commandSuggestions(player, args, parsed);
  }

  @Override
  public void parseArguments(Player player, String[] args, HashMap<String, Object> parsed) {
    super.parseArguments(player, args, parsed);

    if (parsed.get("target") != null || parsed.get("action") == null
        || parsed.get("action").equals(TrackingActions.STOP) || args.length < 3)
      return;

    for (OfflinePlayer candidate : sc.getServer().getOfflinePlayers())
      if (candidate.getName().equalsIgnoreCase(args[2])) {
        parsed.put("target", candidate.getName());
        return;
      }
  }

  @Override
  public boolean perform(Player player, String command, TrackingActions action, String target, String[] args) {
    if (target == null) {
      sc.sendMessage(player, "missing_target");
      return true;
    }

    switch (action) {
      case ASK:
        if (limitReached(player, TrackingActions.START, true, null)) break;

        Player targetPlayer = sc.getServer().getPlayer(target);
        if (targetPlayer == null)
          sc.sendMessage(player, "target_not_found", ImmutableMap.of("target", target));
        else {
          sendPlayerTrackerRequest(player, targetPlayer);
          sc.sendMessage(player, "commands." + command + "." + trackerID() + ".ASK",
              ImmutableMap.of("target", target));
        }
        break;

      case START:
        if (!activate(player, args[2], true)) break;
        sc.sendMessage(player, "commands." + command + "." + trackerID() + ".START",
            ImmutableMap.of("target", args[2]));
        break;

      case STOP:
        disable(player, args[2]);
        sc.sendMessage(player, "commands." + command + "." + trackerID() + ".STOP",
            ImmutableMap.of("target", target));
        break;

      default:
        return false;
    }

    return true;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String cmd = command.getName().toLowerCase();

    switch (cmd) {
      case "scompass-track-accept":
      case "scompass-track-deny":
        if (!(sender instanceof Player) || args.length != 1) return false;
        return respondPlayerTrackerRequest((Player) sender, args[0], (cmd.equals("scompass-track-accept")));
    }

    return false;
  }

  //----------------------------------------------------------------------------------------------
  // TabCompleter methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    switch (command.getName().toLowerCase()) {
      case "sctaccept":
      case "sctdeny":
        if (!(sender instanceof Player) || args.length != 1) return null;

        Set<UUID> candidates = getPlayerTrackerRequests((Player) sender);

        if (candidates == null) return null;

        List<String> list = new ArrayList<String>();

        for (UUID uid : candidates) {
          Player candidate = sc.getServer().getPlayer(uid);

          if (candidate != null && candidate.getName().toLowerCase().startsWith(args[0].toLowerCase()))
            list.add(candidate.getName());
        }

        return list;

      default:
        return null;
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Specific methods
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

    if (requests.containsKey(pUid) && requests.get(pUid).containsKey(sUid)) return requests.get(pUid).get(sUid);

    return 0;
  }

  public void removePlayerTrackerRequest(Player player, Player seeker) {
    UUID pUid = player.getUniqueId(), sUid = seeker.getUniqueId();

    if (requests.containsKey(pUid) && requests.get(pUid).containsKey(sUid)) requests.get(pUid).remove(sUid);
  }

  public boolean respondPlayerTrackerRequest(Player player, String seekerName, boolean accept) {
    Player seeker = sc.getServer().getPlayer(seekerName);

    if (seeker == null || !seeker.isOnline()) return true;

    Long request = getPlayerTrackerRequest(player, seeker);

    if (request == 0) {
      sc.sendMessage(player, "request_expired", ImmutableMap.of("player", seekerName));
      return true;
    }

    removePlayerTrackerRequest(player, seeker);

    ImmutableMap<String, String> placeholders = ImmutableMap.of("player", seekerName, "target", player.getName());

    if (accept) {
      String[] keys = {
          "commands.sctrack." + trackerID() + ".request.accepted.target",
          "commands.sctrack." + trackerID() + ".request.accepted.player" };

      if (!activate(seeker, player.getName(), false))
        for (int i = 0; i < keys.length; i++) keys[i] += "_limit_reached";

      sc.sendMessage(player, keys[0], placeholders);
      sc.sendMessage(seeker, keys[1], placeholders);
    }
    else {
      sc.sendMessage(player, "commands.sctrack." + trackerID() + ".request.refused.target", placeholders);
      sc.sendMessage(seeker, "commands.sctrack." + trackerID() + ".request.refused.player", placeholders);
    }

    return true;
  }

  public void sendPlayerTrackerRequest(Player hunter, Player target) {
    Map<String, Map<String, String>> commands = new HashMap<String, Map<String, String>>();

    for (String action : ImmutableList.of("accept", "deny")) {
      commands.put("{" + action + "}", ImmutableMap.of(
          "text", sc.prepareMessage("commands.sctrack." + trackerID() + ".request." + action),
          "click", "/sct" + action + " " + hunter.getName(),
          "hover", sc.prepareMessage("commands.sctrack." + trackerID() + ".request." + action + "_hover",
              ImmutableMap.of("player", hunter.getName()))));
    }

    setPlayerTrackerRequest(hunter, target);

    target.spigot().sendMessage(sc.createClickableMessage(sc.prepareMessage(
        "commands.sctrack." + trackerID() + ".request.message", ImmutableMap.of("player", hunter.getName())),
        commands));
  }

  public void setPlayerTrackerRequest(Player hunter, Player target) {
    UUID hUid = hunter.getUniqueId(), tUid = target.getUniqueId();

    if (!requests.containsKey(tUid))
      requests.put(tUid, new HashMap<UUID, Long>());
    else
      cleanPlayerTrackerRequests(target);

    requests.get(tUid).put(hUid,
        CacheUtil.now() + sc.config.getInt("tracker_settings.request_duration") * 1000);
  }
}
