package me.arboriginal.SimpleCompass.compasses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import me.arboriginal.SimpleCompass.managers.TaskManager.TasksTypes;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public abstract class AbstractCompass {
  protected SimpleCompass  sc;
  protected CompassTypes   type;
  protected BukkitRunnable task    = null;
  protected String         warning = "";

  public enum CompassTypes {
    ACTIONBAR, BOSSBAR,
  }

  public enum CompassModes {
    MODE180, MODE360,
  }

  public Player owner;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public AbstractCompass(SimpleCompass plugin, Player player, CompassTypes compassTypes) {
    sc    = plugin;
    type  = compassTypes;
    owner = player;

    init();
    refresh();
  }

  // ----------------------------------------------------------------------------------------------
  // Abstract methods
  // ----------------------------------------------------------------------------------------------

  public abstract void display(String datas);

  // ----------------------------------------------------------------------------------------------
  // Default methods
  // ----------------------------------------------------------------------------------------------

  public void delete() {
    sc.tasks.clear(TasksTypes.REFRESH_STATUS, owner);

    if (task != null) task.cancel();
    if (sc.compasses.hasRequiredItems(owner, type, false)) return;

    String message = sc.prepareMessage("warnPlayerNoMoreFuel");
    if (message.isEmpty()) return;

    warning = message;
    display(warning);
  }

  public void init() {}

  public void refresh() {
    display(buildCompassDatas());
  }

  // ----------------------------------------------------------------------------------------------
  // Private methods
  // ----------------------------------------------------------------------------------------------

  private String buildCompassDatas() {
    double rotation = (owner.getEyeLocation().getYaw() - 180) % 360;

    if (rotation < 0) rotation += 360;

    CompassModes mode = sc.datas.compassModeGet(owner, type);

    String prefix = "compass." + type + "." + mode;
    String sep    = sc.config.getString(prefix + ".separator_value");
    String cSep   = sc.config.getString(prefix + ".separator_color");
    String cOn    = sc.config.getString(prefix + ".active_color");
    String cOff   = sc.config.getString(prefix + ".inactive_color");
    String nOn    = sc.config.getString(prefix + ".active_north_color");
    String nOff   = sc.config.getString(prefix + ".north_color");
    String west   = sc.config.getString(prefix + ".cardinals.west");
    String north  = sc.config.getString(prefix + ".cardinals.north");
    String east   = sc.config.getString(prefix + ".cardinals.east");
    String south  = sc.config.getString(prefix + ".cardinals.south");
    String datas  = sep + "♤" + sep + "♡" + sep + "♢" + sep + "♧";
    int    start  = (int) Math.round(rotation * datas.length() / 360);
    char   face   = getFacing();

    datas = datas.substring(start) + datas.substring(0, start);

    if (mode.equals(CompassModes.MODE180)) {
      int strip = (int) Math.ceil(datas.length() / 4);
      datas = datas.substring(strip - 1, datas.length() - strip + 1);
    }

    datas = injectActivatedTrackers(datas, cSep);

    return sc.config.getString(prefix + ".before") + cSep
        + datas // @formatter:off
            .replace("♤", ((face == 'W') ? cOn : cOff) + west  + cSep)
            .replace("♡", ((face == 'N') ? nOn : nOff) + north + cSep)
            .replace("♢", ((face == 'E') ? cOn : cOff) + east  + cSep)
            .replace("♧", ((face == 'S') ? cOn : cOff) + south + cSep)
        + sc.config.getString(prefix + ".after"); // @formatter:on
  }

  private char getFacing() {
    try {
      if (owner.getClass().getMethod("myMethodToFind", (Class<?>[]) null) != null)
        return owner.getFacing().toString().charAt(0);
    }
    catch (Exception e) {}
    // Use this as a fallback for Spigot version (like 1.12) which doesn't support player.getFacing()
    return Arrays.asList('S', 'W', 'N', 'E').get(Math.round(owner.getLocation().getYaw() / 90f) & 0x3);
  }

  private HashMap<String, ArrayList<double[]>> getActiveTargets() { // @formatter:off
    String cacheKey = "trackers." + type;
    @SuppressWarnings("unchecked")
    HashMap<String, ArrayList<double[]>> trackers
      = (HashMap<String, ArrayList<double[]>>) sc.cache.get(owner.getUniqueId(), cacheKey);

    if (trackers == null) {
      trackers = sc.targets.getTargetsCoords(owner);
      sc.cache.set(owner.getUniqueId(), cacheKey, trackers, sc.config.getInt("delays.trackers_list"));
    }

    return trackers;
  } // @formatter:on

  private String injectActivatedTrackers(String compass, String sepColor) {
    HashMap<String, ArrayList<double[]>> targets = getActiveTargets();
    if (targets.isEmpty()) return compass;

    Location refPos = owner.getEyeLocation();

    HashMap<String, String> placeholders = new HashMap<String, String>();

    for (String type : sc.targets.trackersPriority) {
      ArrayList<double[]> coords  = targets.get(type);
      AbstractTracker     tracker = sc.trackers.get(type);
      if (coords == null || tracker == null) continue;

      String marker = tracker.settings.getString("settings.temp");
      String symbol = tracker.settings.getString("settings.symbol");
      placeholders.put(marker, symbol + sepColor);

      for (double[] target : coords) {
        Vector blockDirection = new Location(owner.getWorld(), target[0], refPos.getY(), target[1])
            .subtract(refPos).toVector().normalize();

        Vector  lookAt   = refPos.getDirection().setY(0);
        boolean viewable = (lookAt.dot(blockDirection) > 0);
        double  angle    = Math.toDegrees(blockDirection.angle(lookAt.crossProduct(new Vector(0, 1, 0))));
        if (!viewable) angle = (angle > 90) ? 180 : 0;
        int start = compass.length() - (int) Math.round(2 * angle * compass.length() / 360);

        compass = (start < 2) ? marker + compass.substring(start + 1)
            : compass.substring(0, start - 1) + marker + compass.substring(start);
      }
    }

    for (String placeholder : placeholders.keySet())
      compass = compass.replaceAll(placeholder, placeholders.get(placeholder));

    return compass;
  }
}
