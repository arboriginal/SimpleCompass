package me.arboriginal.SimpleCompass.utils;

import java.util.ArrayList;
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
import me.arboriginal.SimpleCompass.commands.TrackCommand.TrackingActions;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.managers.CompassManager.RequirementsSections;
import me.arboriginal.SimpleCompass.managers.TrackerManager.TrackerTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.OptionUtil.CompassOptions;

public class ConfigUtil {
  private List<ConfigError> errors;
  private SimpleCompass     plugin;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public ConfigUtil(SimpleCompass main) {
    plugin = main;
  }

  // ----------------------------------------------------------------------------------------------
  // Public methods
  // ----------------------------------------------------------------------------------------------

  public List<ConfigError> validate(FileConfiguration configuration) {
    clearErrors();

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
    fixValue(key, plugin.config.getDefaults().get(key));
  }

  private void fixValue(String key, Object value) {
    plugin.config.set(key, value);
  }

  private void validateBossbarAttributes() {
    try {
      BarColor.valueOf(plugin.config.getString("compass.BOSSBAR.attributes.color"));
    }
    catch (Exception e) {
      addError("invalid_bossbar_color");
      fixValue("compass.BOSSBAR.attributes.color");
    }

    try {
      BarStyle.valueOf(plugin.config.getString("compass.BOSSBAR.attributes.style"));
    }
    catch (Exception e) {
      addError("invalid_bossbar_style");
      fixValue("compass.BOSSBAR.attributes.style");
    }

    ConfigurationSection levels = plugin.config
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
    String fillChar = plugin.config.getString("compass." + key + ".cardinals.filling_char");

    if (fillChar.isEmpty()) return;

    List<String> cardinals = ImmutableList.of("east", "north", "south", "west");

    int maxLength = 0;

    for (String cardinal : cardinals)
      maxLength = Math.max(maxLength, plugin.config.getString("compass." + key + ".cardinals." + cardinal).length());

    for (String cardinal : cardinals) {
      String word = plugin.config.getString("compass." + key + ".cardinals." + cardinal);

      if (word.length() < maxLength) {
        word = StringUtils.repeat(fillChar, (int) Math.floor((double) (maxLength - word.length()) / 2)) + word
            + StringUtils.repeat(fillChar, (int) Math.ceil((double) (maxLength - word.length()) / 2));

        fixValue("compass." + key + ".cardinals." + cardinal, word);
        addError("cardinal_length", ImmutableMap.of("key", key, "cardinal", cardinal));
      }
    }
  }

  private void validateDefaultSettings(CompassTypes type) {
    try {
      CompassOptions.valueOf(plugin.config.getString("compass." + type + ".default.option"));
    }
    catch (Exception e) {
      addError("invalid_choice", ImmutableMap.of("type", "" + type, "key", "option"));
      fixValue("compass." + type + ".default.option");
    }

    try {
      CompassModes.valueOf(plugin.config.getString("compass." + type + ".default.mode"));
    }
    catch (Exception e) {
      addError("invalid_choice", ImmutableMap.of("type", "" + type, "key", "mode"));
      fixValue("compass." + type + ".default.mode");
    }
  }

  private List<String> validateRequiredItems(CompassTypes type, RequirementsSections section) {
    List<?>      list  = plugin.config.getList("compass." + type + ".require.items." + section);
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
    List<String> keys = new ArrayList<String>(), modified = new ArrayList<String>();

    for (TrackingActions action : TrackingActions.values()) keys.add("tracker_settings.actions." + action);
    for (TrackerTypes tracker : TrackerTypes.values()) keys.add("tracker_settings.trackers." + tracker + ".name");

    for (String key : keys)
      if (plugin.config.getString(key).contains(" ")) {
        plugin.config.getString(plugin.config.getString(key).replaceAll(" ", ""));

        modified.add(key);
      }

    if (!modified.isEmpty())
      addError("invalid_names", ImmutableMap.of("modified", String.join(" ,", modified)));

    List<TrackerTypes> priorities = new ArrayList<TrackerTypes>();

    for (Object priority : plugin.config.getList("tracker_settings.priority"))
      if (priority instanceof String) {
        try {
          TrackerTypes type = TrackerTypes.valueOf((String) priority);

          if (!priorities.contains(type)) priorities.add(type);
        }
        catch (Exception e) {}
      }

    if (priorities.size() != TrackerTypes.values().length) {
      fixValue("tracker_settings.priority");
      addError("invalid_priorities");
    }
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
