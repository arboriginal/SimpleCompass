package me.arboriginal.SimpleCompass.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.commands.AbstractCommand.CompassOptions;
import me.arboriginal.SimpleCompass.commands.AbstractCommand.SubCmds;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.managers.CompassManager.RequirementsSections;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker.TrackingActions;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class ConfigUtil {
  private List<ConfigError> errors;
  private SimpleCompass     sc;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public ConfigUtil(SimpleCompass plugin) {
    sc = plugin;
  }

  // ----------------------------------------------------------------------------------------------
  // Public methods
  // ----------------------------------------------------------------------------------------------

  public List<ConfigError> validate(FileConfiguration configuration) {
    clearErrors();

    List<String> modified = new ArrayList<String>();

    modified.addAll(validateCustomNames(TrackingActions.values(), "actions"));
    modified.addAll(validateCustomNames(SubCmds.values(), "subcommands"));

    if (!modified.isEmpty())
      addError("invalid_names", ImmutableMap.of("modified", String.join(" ,", modified)));

    validateTrackerSettings();
    validateBossbarAttributes();

    for (CompassTypes type : CompassTypes.values()) {
      validateDefaultSettings(type);

      for (CompassModes mode : CompassModes.values()) validateCardinals(type, mode);

      boolean hasRequirements = false;

      for (RequirementsSections section : RequirementsSections.values())
        if (!validateRequiredItems(type, section).isEmpty()) hasRequirements = true;

      if (!hasRequirements) {
        fixValue("compass." + type + ".require.items", new MemoryConfiguration());
        fixValue("compass." + type + ".require.consume", false);
      }
    }

    return errors;
  }

  public void clearErrors() {
    errors = new ArrayList<ConfigError>();
  }

  // ----------------------------------------------------------------------------------------------
  // Private methods
  // ----------------------------------------------------------------------------------------------

  private void addError(String errorKey) {
    addError(errorKey, null);
  }

  private void addError(String errorKey, Map<String, String> placeholders) {
    errors.add(new ConfigError(errorKey, placeholders));
  }

  private void fixValue(String key) {
    fixValue(key, sc.config.getDefaults().get(key));
  }

  private void fixValue(String key, Object value) {
    sc.config.set(key, value);
  }

  private void validateBossbarAttributes() {
    try {
      BarColor.valueOf(sc.config.getString("compass.BOSSBAR.attributes.color"));
    }
    catch (Exception e) {
      addError("invalid_bossbar_color");
      fixValue("compass.BOSSBAR.attributes.color");
    }

    try {
      BarStyle.valueOf(sc.config.getString("compass.BOSSBAR.attributes.style"));
    }
    catch (Exception e) {
      addError("invalid_bossbar_style");
      fixValue("compass.BOSSBAR.attributes.style");
    }

    ConfigurationSection levels = sc.config
        .getConfigurationSection("compass.BOSSBAR.attributes.elytra_durability.levels");

    if (levels == null) return;

    TreeMap<Integer, String> sortedLevel = new TreeMap<Integer, String>();

    for (String level : levels.getKeys(false)) {
      try {
        BarColor.valueOf(levels.get(level).toString());
        sortedLevel.put(Integer.parseInt(level), levels.get(level).toString());
      }
      catch (Exception e) {}
    }

    if (levels.getKeys(false).size() != sortedLevel.size()) addError("invalid_bossbar_color_level");

    fixValue("compass.BOSSBAR.attributes.elytra_durability.levels", sortedLevel);
  }

  private void validateCardinals(CompassTypes type, CompassModes mode) {
    String key      = type + "." + mode;
    String fillChar = sc.config.getString("compass." + key + ".cardinals.filling_char");

    if (fillChar.isEmpty()) return;

    List<String> cardinals = ImmutableList.of("east", "north", "south", "west");

    int maxLength = 0;

    for (String cardinal : cardinals)
      maxLength = Math.max(maxLength, sc.config.getString("compass." + key + ".cardinals." + cardinal).length());

    for (String cardinal : cardinals) {
      String word = sc.config.getString("compass." + key + ".cardinals." + cardinal);

      if (word.length() < maxLength) {
        word = StringUtils.repeat(fillChar, (int) Math.floor((double) (maxLength - word.length()) / 2)) + word
            + StringUtils.repeat(fillChar, (int) Math.ceil((double) (maxLength - word.length()) / 2));

        fixValue("compass." + key + ".cardinals." + cardinal, word);
        addError("cardinal_length", ImmutableMap.of("key", key, "cardinal", cardinal));
      }
    }
  }

  private List<String> validateCustomNames(Object[] values, String section) {
    List<String> modified = new ArrayList<String>();

    for (Object obj : values) {
      String key = section + "." + obj;

      if (sc.locale.getString(key).contains(" ")) {
        sc.locale.getString(sc.locale.getString(key).replaceAll(" ", ""));
        modified.add(key);
      }
    }

    return modified;
  }

  private void validateDefaultSettings(CompassTypes type) {
    try {
      CompassOptions.valueOf(sc.config.getString("compass." + type + ".default.option"));
    }
    catch (Exception e) {
      addError("invalid_choice", ImmutableMap.of("type", "" + type, "key", "option"));
      fixValue("compass." + type + ".default.option");
    }

    try {
      CompassModes.valueOf(sc.config.getString("compass." + type + ".default.mode"));
    }
    catch (Exception e) {
      addError("invalid_choice", ImmutableMap.of("type", "" + type, "key", "mode"));
      fixValue("compass." + type + ".default.mode");
    }
  }

  private List<String> validateRequiredItems(CompassTypes type, RequirementsSections section) {
    List<?>      list  = sc.config.getList("compass." + type + ".require.items." + section);
    List<String> items = new ArrayList<String>();

    if (list.size() == 0) return items;

    if (list.contains("AIR")) {
      fixValue("compass." + type + ".require.items." + section, items);

      return items;
    }

    for (Object item : list) {
      try {
        Material.valueOf((String) item);

        items.add((String) item);
      }
      catch (Exception e) {}
    }

    if (list.size() > items.size()) {
      addError("invalid_items", ImmutableMap.of("section", "" + section, "type", "" + type,
          "ignored", "" + (list.size() - items.size())));

      fixValue("compass." + type + ".require.items." + section, items);
    }

    return items;
  }

  private void validateTrackerSettings() {
    Iterator<String> it = sc.trackers.keySet().iterator();
    while (it.hasNext()) {
      String trackerID = it.next();

      if (!((AbstractTracker) sc.trackers.get(trackerID)).trackerName().toLowerCase().matches("^[a-z0-9]+$")) {
        it.remove();
        sc.sendMessage(sc.getServer().getConsoleSender(), "tracker_disabled_invalid_name",
            ImmutableMap.of("tracker", trackerID));
      }
    }
    
    List<String> userPriorities = sc.config.getStringList("trackers_priorities");
    List<String> readPriorities = new ArrayList<String>();

    for (String priority : sc.config.getStringList("trackers_priorities")) { // @formatter:off
      if (!sc.trackers.containsKey(priority)) userPriorities.remove(priority);
      else if (!readPriorities.contains(priority)) readPriorities.add(priority);
    } // @formatter:on

    if (readPriorities.size() != sc.trackers.size()) {
      fixValue("trackers_priorities");
      addError("invalid_priorities");
    }

    for (String tracker : sc.trackers.keySet()) if (!userPriorities.contains(tracker)) userPriorities.add(tracker);
    fixValue("trackers_priorities", userPriorities);
  }

  // ----------------------------------------------------------------------------------------------
  // Private classes
  // ----------------------------------------------------------------------------------------------

  public static class ConfigError {
    public final String              errorKey;
    public final Map<String, String> placeholders;

    public ConfigError(String k, Map<String, String> p) {
      errorKey     = k;
      placeholders = p;
    }
  }
}
