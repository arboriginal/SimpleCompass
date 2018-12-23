package me.arboriginal.SimpleCompass.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.managers.TaskManager.TasksTypes;
import me.arboriginal.SimpleCompass.managers.TrackerManager.TrackerTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.CacheUtil;
import me.arboriginal.SimpleCompass.utils.OptionUtil.CompassOptions;

public class DataManager {
  private SimpleCompass     plugin;
  private File              datasFile;
  private YamlConfiguration usersDatas;

  //-----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public DataManager(SimpleCompass main) {
    plugin     = main;
    usersDatas = new YamlConfiguration();
    datasFile  = new File(plugin.getDataFolder(), "usersDatas.yml");

    if (datasFile.exists())
      usersDatas = YamlConfiguration.loadConfiguration(datasFile);
    else
      saveUserDatas();
  }

  // ----------------------------------------------------------------------------------------------
  // General methods
  // ----------------------------------------------------------------------------------------------

  public String getKey(Player player, String key) {
    return player.getUniqueId() + "." + key;
  }

  public void saveUserDatas() {
    try {
      if (!datasFile.exists()) {
        datasFile.createNewFile();
      }

      usersDatas.save(datasFile);
    }
    catch (IOException e) {
      plugin.getLogger().severe(plugin.prepareMessage("file_not_writable"));
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Cooldown methods
  // ----------------------------------------------------------------------------------------------

  public String getCooldownKey(Player player, String cooldown) {
    return getKey(player, "cooldowns." + cooldown);
  }

  public Long getCooldown(Player player, String cooldown) {
    String dataKey = getCooldownKey(player, cooldown);

    if (usersDatas.contains(dataKey)) {
      Long left = usersDatas.getLong(dataKey) - CacheUtil.now();

      if (left > 0) return left;
    }

    return 0L;
  }

  public void setCooldown(Player player, String cooldown, int delay) {
    usersDatas.set(getCooldownKey(player, cooldown), CacheUtil.now() + delay * 1000);

    saveUserDatas();
  }

  // Book cooldown

  public Long getBookCooldown(Player player) {
    return getCooldown(player, "interface_book");
  }

  public void setBookCooldown(Player player) {
    setCooldown(player, "interface_book", plugin.config.getInt("interface.give_book_cooldown"));
  }

  // Consume cooldown

  public Long getConsumeCooldown(Player player, CompassTypes type) {
    return getCooldown(player, "consume_" + type);
  }

  public void setConsumeCooldown(Player player, CompassTypes type) {
    setCooldown(player, "consume_" + type, plugin.config.getInt("compass." + type + ".require.duration"));
  }

  // ----------------------------------------------------------------------------------------------
  // Compass options methods
  // ----------------------------------------------------------------------------------------------

  public String getCompassOptionKey(Player player, CompassTypes type) {
    return getKey(player, type + ".option");
  }

  public CompassOptions getCompassOption(Player player, CompassTypes type) {
    String key = getCompassOptionKey(player, type);

    return CompassOptions.valueOf(usersDatas.contains(key) ? usersDatas.getString(key)
        : plugin.config.getDefaults().getString("compass." + type + ".default.option"));
  }

  public void setCompassOption(Player player, CompassTypes type, CompassOptions option) {
    if (plugin.config.getBoolean("single_compass_mode") && !option.equals(CompassOptions.DISABLED))
      for (CompassTypes otherType : CompassTypes.values())
        if (!type.equals(otherType))
          usersDatas.set(getCompassOptionKey(player, otherType), CompassOptions.DISABLED.toString());

    usersDatas.set(getCompassOptionKey(player, type), option.toString());
    saveUserDatas();

    plugin.tasks.set(TasksTypes.valueOf("REFRESH_" + type), player);
  }

  // ----------------------------------------------------------------------------------------------
  // Compass modes methods
  // ----------------------------------------------------------------------------------------------

  public String getCompassModeKey(Player player, CompassTypes type) {
    return getKey(player, type + ".mode");
  }

  public CompassModes getCompassMode(Player player, CompassTypes type) {
    String key = getCompassModeKey(player, type);

    return CompassModes.valueOf(usersDatas.contains(key) ? usersDatas.getString(key)
        : plugin.config.getDefaults().getString("compass." + type + ".default.mode"));
  }

  public void setCompassMode(Player player, CompassTypes type, CompassModes mode) {
    usersDatas.set(getCompassModeKey(player, type), mode.toString());
    saveUserDatas();

    plugin.tasks.set(TasksTypes.valueOf("REFRESH_" + type), player);
  }

  // ----------------------------------------------------------------------------------------------
  // Player trackers activation methods
  // ----------------------------------------------------------------------------------------------

  public void playerAtivatedTrackerAdd(Player player, TrackerTypes type, String name) {
    List<String> list = playerAtivatedTrackersList(player, type);

    if (list.contains(name)) return;

    list.add(name);

    playerAtivatedTrackersSave(player, type, list);
  }

  public void playerAtivatedTrackerDel(Player player, TrackerTypes type, String name) {
    List<String> list = playerAtivatedTrackersList(player, type);

    if (!list.contains(name)) return;

    list.remove(name);

    playerAtivatedTrackersSave(player, type, list);
  }

  public List<String> playerAtivatedTrackersList(Player player, TrackerTypes type) {
    String       key  = getKey(player, "active_trackers." + type);
    List<String> list = new ArrayList<String>();

    if (usersDatas.getList(key) != null)
      for (Object tracker : usersDatas.getList(key))
        if (tracker instanceof String) list.add((String) tracker);

    return list;
  }

  public void playerAtivatedTrackersSave(Player player, TrackerTypes type, List<String> list) {
    usersDatas.set(getKey(player, "active_trackers." + type), list);
    saveUserDatas();

    plugin.tasks.set(TasksTypes.REFRESH_ACTIONBAR, player);
    plugin.tasks.set(TasksTypes.REFRESH_BOSSBAR, player);
  }

  // ----------------------------------------------------------------------------------------------
  // Coords tracker methods
  // ----------------------------------------------------------------------------------------------

  public String getPlayerRegisteredCoordsKey(Player player) {
    return getPlayerRegisteredCoordsKey(player, null);
  }

  public String getPlayerRegisteredCoordsKey(Player player, String name) {
    return getKey(player, "coords" + (name == null ? "" : "." + name));
  }

  public double[] getPlayerRegisteredCoords(Player player, String name) {
    String key = getPlayerRegisteredCoordsKey(player, name);

    if (usersDatas.contains(key + ".x") && usersDatas.contains(key + ".z")) {
      try {
        double[] coords = { usersDatas.getDouble(key + ".x"), usersDatas.getDouble(key + ".z") };
        return coords;
      }
      catch (Exception e) {}
    }

    return null;
  }

  public List<String> getPlayerRegisteredCoordsList(Player player) {
    List<String> list = new ArrayList<String>();

    String key = getPlayerRegisteredCoordsKey(player);

    if (usersDatas.contains(key)) {
      list.addAll(usersDatas.getConfigurationSection(key).getKeys(false));
    }

    return list;
  }

  public boolean removePlayerRegisteredCoords(Player player, String name) {
    String key = getPlayerRegisteredCoordsKey(player, name);

    if (usersDatas.contains(key)) {
      plugin.trackers.disableTracker(TrackerTypes.COORDS, player, name);
      usersDatas.set(key, null);
      saveUserDatas();

      return true;
    }

    return false;
  }

  public boolean setPlayerRegisteredCoords(Player player, String name, double[] coords) {
    String key = getPlayerRegisteredCoordsKey(player, name);

    if (usersDatas.contains(key)) return false;

    if (coords == null) {
      double[] currentPosition = { player.getLocation().getX(), player.getLocation().getZ() };
      coords = currentPosition;
    }

    usersDatas.set(key + ".x", coords[0]);
    usersDatas.set(key + ".z", coords[1]);
    saveUserDatas();

    return true;
  }
}
