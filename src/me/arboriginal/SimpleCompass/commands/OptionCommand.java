package me.arboriginal.SimpleCompass.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.OptionUtil;

public class OptionCommand extends OptionUtil implements CommandExecutor, TabCompleter {
  //-----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public OptionCommand(SimpleCompass main) {
    super(main, "scoption");
  }

  // ----------------------------------------------------------------------------------------------
  // CommandExecutor methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player)) {
      plugin.sendMessage(sender, "command_only_for_players");

      return false;
    }

    return performCommandOption(sender, args);
  }

  // ----------------------------------------------------------------------------------------------
  // TabCompleter methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    List<String> list = new ArrayList<>();

    switch (args.length) {
      case 1:
        for (String option : allowedOptions(sender))
          if (option.toLowerCase().startsWith(args[0].toLowerCase())) list.add(option);
        break;

      case 2:
        if (allowedOptions(sender).contains(args[0]))
          for (CompassTypes type : allowedTypes(sender))
            if (type.toString().toLowerCase().startsWith(args[1].toLowerCase())) list.add(type.toString());
        break;
    }

    return list;
  }

  // ----------------------------------------------------------------------------------------------
  // SimpleCompassOptions methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public void showOptions(Player player, CompassTypes modified) {
    plugin.sendMessage(player, optKey + ".header");

    for (CompassTypes type : allowedTypes(player)) {
      CompassModes                     typeMode = plugin.datas.getCompassMode(player, type);
      CompassOptions                   selected = plugin.datas.getCompassOption(player, type);
      Map<String, Map<String, String>> commands = new LinkedHashMap<String, Map<String, String>>();

      for (CompassModes mode : CompassModes.values())
        commands.put("{" + mode + "}", clickableOption(type, mode, typeMode));

      player.spigot().sendMessage(
          plugin.createClickableMessage(plugin.prepareMessage(optKey + ".content",
              ImmutableMap.of("type", plugin.locale.getString("types." + type))), commands));

      commands = new LinkedHashMap<String, Map<String, String>>();

      for (String option : allowedOptions(player)) {
        if (option.equals(CompassModes.MODE180.toString()) || option.equals(CompassModes.MODE360.toString())) continue;

        commands.put("{" + option + "}", clickableOption(type, option, selected));
      }

      player.spigot().sendMessage(plugin.createClickableMessage(String.join("", commands.keySet()), commands));
    }

    plugin.sendMessage(player, optKey + ".footer");

    if (modified != null) plugin.sendMessage(player, optKey + ".saved");
  }
}
