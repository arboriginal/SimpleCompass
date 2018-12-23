package me.arboriginal.SimpleCompass.compasses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import me.arboriginal.SimpleCompass.managers.TaskManager.TasksTypes;
import me.arboriginal.SimpleCompass.managers.TrackerManager.TrackerTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public abstract class AbstractCompass {
  protected SimpleCompass  plugin;
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

  public AbstractCompass(SimpleCompass main, Player player, CompassTypes compassTypes) {
    plugin = main;
    type   = compassTypes;
    owner  = player;

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
    plugin.tasks.clear(TasksTypes.REFRESH_STATUS, owner);

    if (task != null) task.cancel();
    if (plugin.compasses.hasRequiredItems(owner, type, false)) return;

    String message = plugin.prepareMessage("warnPlayerNoMoreFuel");

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

    CompassModes mode = plugin.datas.getCompassMode(owner, type);

    String prefix = "compass." + type + "." + mode;
    String sep    = plugin.config.getString(prefix + ".separator_value");
    String cSep   = plugin.config.getString(prefix + ".separator_color");
    String cOn    = plugin.config.getString(prefix + ".active_color");
    String cOff   = plugin.config.getString(prefix + ".inactive_color");
    String nOn    = plugin.config.getString(prefix + ".active_north_color");
    String nOff   = plugin.config.getString(prefix + ".north_color");
    String west   = plugin.config.getString(prefix + ".cardinals.west");
    String north  = plugin.config.getString(prefix + ".cardinals.north");
    String east   = plugin.config.getString(prefix + ".cardinals.east");
    String south  = plugin.config.getString(prefix + ".cardinals.south");
    String datas  = sep + "♤" + sep + "♡" + sep + "♢" + sep + "♧";
    int    start  = (int) Math.round(rotation * datas.length() / 360);
    char   face   = getFacing();

    datas = datas.substring(start) + datas.substring(0, start);

    if (mode.equals(CompassModes.MODE180)) {
      int strip = (int) Math.ceil(datas.length() / 4);
      datas = datas.substring(strip - 1, datas.length() - strip + 1);
    }

    datas = injectActivatedTrackers(datas, cSep);

    return plugin.config.getString(prefix + ".before") + cSep
        + datas
            .replace("♤", ((face == 'W') ? cOn : cOff) + west + cSep)
            .replace("♡", ((face == 'N') ? nOn : nOff) + north + cSep)
            .replace("♢", ((face == 'E') ? cOn : cOff) + east + cSep)
            .replace("♧", ((face == 'S') ? cOn : cOff) + south + cSep)
        + plugin.config.getString(prefix + ".after");
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

  private String injectActivatedTrackers(String compass, String sepColor) {
    HashMap<TrackerTypes, ArrayList<double[]>> trackers = plugin.trackers.getActivatedTrackersCoords(owner);

    if (trackers.isEmpty()) return compass;

    Location refPos = owner.getEyeLocation();
    Vector   lookAt = refPos.getDirection().setY(0);

    HashMap<String, String> placeholders = new HashMap<String, String>();

    for (TrackerTypes type : plugin.trackers.trackersPriority) {
      ArrayList<double[]> coords = trackers.get(type);

      if (coords == null) continue;

      String marker = plugin.config.getString("tracker_settings.trackers." + type + ".temp");
      String symbol = plugin.config.getString("tracker_settings.trackers." + type + ".symbol");

      placeholders.put(marker, symbol + sepColor);

      for (double[] tracker : trackers.get(type)) {
        Vector blockDirection = new Location(owner.getWorld(), tracker[0], refPos.getY(), tracker[1])
            .subtract(refPos).toVector().normalize();

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
