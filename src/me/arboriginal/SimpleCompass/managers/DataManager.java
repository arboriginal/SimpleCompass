package me.arboriginal.SimpleCompass.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import me.arboriginal.SimpleCompass.commands.AbstractCommand.CompassOptions;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.managers.TaskManager.TasksTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.CacheUtil;

public class DataManager {
  private SimpleCompass sc;
  private File          file;

  public YamlConfiguration users;

  //-----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public DataManager(SimpleCompass plugin) {
    sc    = plugin;
    users = new YamlConfiguration();
    file  = new File(sc.getDataFolder(), "usersDatas.yml");

    if (file.exists())
      users = YamlConfiguration.loadConfiguration(file);
    else
      saveUserDatas();
  }

  // ----------------------------------------------------------------------------------------------
  // General methods
  // ----------------------------------------------------------------------------------------------

  public String getKey(Player player, String key) {
    return player.getUniqueId() + "." + key;
  }

  public boolean saveUserDatas() {
    try {
      if (!file.exists()) file.createNewFile();

      users.save(file);
      return true;
    }
    catch (IOException e) {
      sc.getLogger().severe(sc.prepareMessage("file_not_writable"));
    }

    return false;
  }

  // ----------------------------------------------------------------------------------------------
  // Cooldown methods
  // ----------------------------------------------------------------------------------------------

  public Long cooldownGet(Player player, String cooldown) {
    String dataKey = cooldownKey(player, cooldown);

    if (users.contains(dataKey)) {
      Long left = users.getLong(dataKey) - CacheUtil.now();

      if (left > 0) return left;
    }

    return 0L;
  }

  public String cooldownKey(Player player, String cooldown) {
    return getKey(player, "cooldowns." + cooldown);
  }

  public void cooldownSet(Player player, String cooldown, int delay) {
    users.set(cooldownKey(player, cooldown), CacheUtil.now() + delay * 1000);
    saveUserDatas();
  }

  // Book cooldown

  public String cooldownBook() {
    return "interface_book";
  }

  public Long cooldownBookGet(Player player) {
    return cooldownGet(player, cooldownBook());
  }

  public void cooldownBookSet(Player player) {
    cooldownSet(player, cooldownBook(), sc.config.getInt("interface.give_book_cooldown"));
  }

  // Consume cooldown

  public String cooldownConsume(CompassTypes type) {
    return "consume_" + type;
  }

  public Long cooldownConsumeGet(Player player, CompassTypes type) {
    return cooldownGet(player, cooldownConsume(type));
  }

  public void cooldownConsumeSet(Player player, CompassTypes type) {
    cooldownSet(player, cooldownConsume(type), sc.config.getInt("compass." + type + ".require.duration"));
  }

  // ----------------------------------------------------------------------------------------------
  // Compass options methods
  // ----------------------------------------------------------------------------------------------

  public CompassOptions compassOptionGet(Player player, CompassTypes type) {
    String key = compassOptionKey(player, type);

    return CompassOptions.valueOf(users.contains(key) ? users.getString(key)
        : sc.config.getDefaults().getString("compass." + type + ".default.option"));
  }

  public String compassOptionKey(Player player, CompassTypes type) {
    return getKey(player, type + ".option");
  }

  public void compassOptionSet(Player player, CompassTypes type, CompassOptions option) {
    if (sc.config.getBoolean("single_compass_mode") && !option.equals(CompassOptions.DISABLED))
      for (CompassTypes otherType : CompassTypes.values())
        if (!type.equals(otherType))
          users.set(compassOptionKey(player, otherType), CompassOptions.DISABLED.toString());

    users.set(compassOptionKey(player, type), option.toString());
    saveUserDatas();

    sc.tasks.set(TasksTypes.valueOf("REFRESH_" + type), player);
  }

  // ----------------------------------------------------------------------------------------------
  // Compass modes methods
  // ----------------------------------------------------------------------------------------------

  public CompassModes compassModeGet(Player player, CompassTypes type) {
    String key = compassModeKey(player, type);

    return CompassModes.valueOf(users.contains(key) ? users.getString(key)
        : sc.config.getDefaults().getString("compass." + type + ".default.mode"));
  }

  public String compassModeKey(Player player, CompassTypes type) {
    return getKey(player, type + ".mode");
  }

  public void compassModeSet(Player player, CompassTypes type, CompassModes mode) {
    users.set(compassModeKey(player, type), mode.toString());
    saveUserDatas();

    sc.tasks.set(TasksTypes.valueOf("REFRESH_" + type), player);
  }

  // ----------------------------------------------------------------------------------------------
  // Tracker targets methods
  // ----------------------------------------------------------------------------------------------

  public boolean activeTargetAdd(Player player, String type, String name) {
    List<String> list = activeTargetsList(player, type);
    if (list.contains(name)) return false;

    list.add(name);
    activeTargetsSave(player, type, list);
    return true;
  }

  public boolean activeTargetDel(Player player, String type, String name) {
    List<String> list = activeTargetsList(player, type);
    if (!list.contains(name)) return false;

    list.remove(name);
    activeTargetsSave(player, type, list);
    return true;
  }

  public String activeTargetsKey(Player player, String type) {
    return getKey(player, "active_targets." + type);
  }

  public List<String> activeTargetsList(Player player, String type) {
    String       key  = activeTargetsKey(player, type);
    List<String> list = new ArrayList<String>();

    if (users.getList(key) != null) for (String tracker : users.getStringList(key)) list.add(tracker);

    return list;
  }

  public void activeTargetsSave(Player player, String type, List<String> list) {
    users.set(activeTargetsKey(player, type), list);
    saveUserDatas();
    sc.tasks.set(TasksTypes.REFRESH_ACTIONBAR, player);
    sc.tasks.set(TasksTypes.REFRESH_BOSSBAR, player);
  }
}
