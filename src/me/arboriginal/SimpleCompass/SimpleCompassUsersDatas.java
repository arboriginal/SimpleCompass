package me.arboriginal.SimpleCompass;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class SimpleCompassUsersDatas {
  private Main              plugin;
  private File              datasFile;
  private YamlConfiguration usersDatas;

  public enum options {
    ALWAYS,
    ELYTRA,
    ELYTRA_VEHICLE,
    VEHICLE,
    DISABLED,
  };

  public enum types {
    ACTIONBAR,
    BOSSBAR,
  };

  public SimpleCompassUsersDatas(Main main) {
    plugin     = main;
    usersDatas = new YamlConfiguration();
    datasFile  = new File(plugin.getDataFolder(), "usersDatas.yml");

    if (datasFile.exists())
      usersDatas = YamlConfiguration.loadConfiguration(datasFile);
    else
      saveUserDatas();
  }

  String getKey(Player player, types key) {
    return getKey(player, key.toString());
  }

  String getKey(Player player, String key) {
    return player.getUniqueId() + "." + key;
  }

  options getPlayerOption(Player player, String type) {
    return getPlayerOption(player, types.valueOf(type));
  }

  options getPlayerOption(Player player, types type) {
    String dataKey = getKey(player, type);

    return options.valueOf(usersDatas.contains(dataKey) ? usersDatas.getString(dataKey)
        : Main.config.getDefaults().getString("default_settings." + type));
  }

  void setPlayerOption(Player player, types type, options option) {
    setPlayerOption(player, type.toString(), option.toString());
  }

  void setPlayerOption(Player player, String type, String option) {
    usersDatas.set(getKey(player, type), option);

    if (Main.config.getBoolean("single_compass_mode") && !option.equals(options.DISABLED.toString())) {
      for (types otherType : types.values()) {
        if (!type.equals(otherType.toString()))
          usersDatas.set(getKey(player, otherType), options.DISABLED.toString());
      }
    }

    saveUserDatas();
    plugin.scm.updateState(player);
  }

  void saveUserDatas() {
    try {
      if (!datasFile.exists()) {
        datasFile.createNewFile();
      }

      usersDatas.save(datasFile);
    }
    catch (IOException e) {
      plugin.getLogger().severe(plugin.prepareMessage("error.file_not_writable"));
    }
  }

  void displayStatus(Player player, List<String> typesList, List<String> optionsList) {
    player.sendMessage(plugin.prepareMessage("messages.status.options_header"));
    player.sendMessage(plugin.prepareMessage("messages.status.options_separator"));

    for (String type : typesList) {
      player.sendMessage(plugin.prepareMessage("messages.status.option_title",
          ImmutableMap.of("type", Main.config.getString("messages.types." + type))));
      displayOptions(player, type, optionsList);
      player.sendMessage(plugin.prepareMessage("messages.status.options_separator"));
    }

    player.sendMessage(plugin.prepareMessage("messages.status.options_footer"));
  }

  void displayOptions(Player player, String type, List<String> optionsList) {
    options playerOption = getPlayerOption(player, type);

    TextComponent optionsComponent = new TextComponent();

    for (String option : optionsList) {
      String clickableText = Main.config.getString("messages.options." + option);
      String optionText    = plugin.prepareMessage(
          "messages.status.option_" + (option.equals(playerOption.toString()) ? "on" : "off"));

      for (BaseComponent component : TextComponent.fromLegacyText(optionText)) {
        if (component instanceof TextComponent && ((TextComponent) component).getText().contains("{option}")) {
          ((TextComponent) component).setText(
              ((TextComponent) component).getText().replace("{option}", clickableText));

          component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
              TextComponent.fromLegacyText(plugin.prepareMessage("messages.status.option_hover",
                  ImmutableMap.of("option", clickableText, "type", Main.config.getString("messages.types." + type))))));
          component
              .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/scompass-option " + option + " " + type));
        }

        optionsComponent.addExtra(component);
      }

    }

    player.spigot().sendMessage(optionsComponent);
  }
}
