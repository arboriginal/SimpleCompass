package me.arboriginal.SimpleCompass;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.UnmodifiableIterator;
import me.arboriginal.SimpleCompass.SimpleCompassManager.sections;
import me.arboriginal.SimpleCompass.SimpleCompassUsersDatas.types;

public class Main extends JavaPlugin {
  public static FileConfiguration config;

  public SimpleCompassUsersDatas udm;
  public SimpleCompassManager    scm;

  // ----------------------------------------------------------------------------------------------
  // JavaPlugin methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public void onEnable() {
    udm = new SimpleCompassUsersDatas(this);
    scm = new SimpleCompassManager(this);

    reloadConfig();

    getServer().getPluginManager().registerEvents(new SimpleCompassListener(this), this);
    getCommand("scompass-option").setExecutor(new SimpleCompassCommand(this));

    for (Player player : getServer().getOnlinePlayers())
      scm.updateState(player);
  }

  @Override
  public void onDisable() {
    for (Player player : getServer().getOnlinePlayers()) {
      scm.removePlayerWarnNoFuel(player);

      for (types type : SimpleCompassUsersDatas.types.values())
        scm.removeCompass(player.getUniqueId(), type);
    }
  }

  @Override
  public void reloadConfig() {
    super.reloadConfig();

    saveDefaultConfig();
    config = getConfig();
    config.options().copyDefaults(true);
    validateConfig();
    saveConfig();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("scompass-reload")) {
      reloadConfig();

      for (Player player : getServer().getOnlinePlayers())
        scm.updateState(player, false);

      sender.sendMessage(prepareMessage("messages.configuration_reloaded"));

      return true;
    }

    return super.onCommand(sender, command, label, args);
  }

  // ----------------------------------------------------------------------------------------------
  // Custom package methods
  // ----------------------------------------------------------------------------------------------

  String prepareMessage(String key) {
    return prepareMessage(key, null);
  }

  String prepareMessage(String key, ImmutableMap<String, String> placeholders) {
    String message = config.getString(key);

    if (placeholders != null) {
      for (UnmodifiableIterator<String> i = placeholders.keySet().iterator(); i.hasNext();) {
        String placeholder = i.next();

        message = message.replace("{" + placeholder + "}", placeholders.get(placeholder));
      }
    }

    return formatMessage(message.replace("{prefix}", config.getString("prefix")));
  }

  String formatMessage(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }

  // ----------------------------------------------------------------------------------------------
  // Custom public methods
  // ----------------------------------------------------------------------------------------------

  Long getCurrentTime() {
    return System.currentTimeMillis();
  }

  // ----------------------------------------------------------------------------------------------
  // Custom private methods
  // ----------------------------------------------------------------------------------------------

  private void validateConfig() {
    try {
      BarColor.valueOf(config.getString("compass.BOSSBAR.attributes.color"));
    }
    catch (Exception e) {
      getLogger().warning(prepareMessage("errors.invalid_bossbar_color"));
      config.set("compass.bossbar.attributes.color",
          config.getDefaults().getString("compass.BOSSBAR.attributes.color"));
    }

    try {
      BarStyle.valueOf(config.getString("compass.BOSSBAR.attributes.style"));
    }
    catch (Exception e) {
      getLogger().warning(prepareMessage("errors.invalid_bossbar_style"));
      config.set("compass.bossbar.attributes.style",
          config.getDefaults().getString("compass.BOSSBAR.attributes.style"));
    }

    for (types type : SimpleCompassUsersDatas.types.values()) {
      try {
        SimpleCompassUsersDatas.options.valueOf(config.getString("default_settings." + type));
      }
      catch (Exception e) {
        getLogger().warning(prepareMessage("errors.invalid_choice", ImmutableMap.of("type", "" + type)));
        config.set("default_settings." + type, config.getDefaults().getString("default_settings." + type));
      }

      for (sections section : sections.values()) {
        List<?>      list  = Main.config.getList("compass." + type + ".require." + section);
        List<String> items = new ArrayList<String>();

        if (list.size() == 0) continue;

        if (list.contains("AIR")) {
          config.set("compass." + type + ".require." + section, items);
          continue;
        }

        for (Object item : list) {
          try {
            Material.valueOf((String) item);
            items.add((String) item);
          }
          catch (Exception e) {}
        }

        if (list.size() > items.size()) {
          getLogger().warning(prepareMessage("errors.invalid_items",
              ImmutableMap.of("section", "" + section, "type", "" + type, "ignored",
                  "" + (list.size() - items.size()))));

          config.set("compass." + type + ".require." + section, items);
        }
      }
    }
  }
}
