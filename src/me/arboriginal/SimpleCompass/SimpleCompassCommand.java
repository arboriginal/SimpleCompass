package me.arboriginal.SimpleCompass;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.SimpleCompassUsersDatas.options;
import me.arboriginal.SimpleCompass.SimpleCompassUsersDatas.types;

public class SimpleCompassCommand implements TabCompleter, CommandExecutor {
  private Main plugin;

  public SimpleCompassCommand(Main main) {
    plugin = main;
  }

  // ----------------------------------------------------------------------------------------------
  // TabCompleter methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    List<String> list = new ArrayList<>();

    switch (args.length) {
      case 1:
        for (String option : allowedOptions(sender)) {
          if (option.toLowerCase().startsWith(args[0].toLowerCase())) list.add(option);
        }
        break;

      case 2:
        if (allowedOptions(sender).contains(args[0])) {
          for (String type : allowedTypes(sender)) {
            if (type.toLowerCase().startsWith(args[1].toLowerCase())) list.add(type);
          }
        }
        break;
    }

    return list;
  }

  // ----------------------------------------------------------------------------------------------
  // CommandExecutor methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!hasAccess(sender, args, true)) return false;

    switch (args.length) {
      case 0:
        plugin.udm.displayStatus((Player) sender, allowedTypes(sender), allowedOptions(sender));
        break;

      case 1:
        if (allowedOptions(sender).size() != 1) {
          sender.sendMessage(plugin.prepareMessage("errors.missing_type"));
          return false;
        }

        plugin.udm.setPlayerOption((Player) sender, allowedOptions(sender).get(0), args[0]);
        break;

      case 2:
        plugin.udm.setPlayerOption((Player) sender, args[1], args[0]);
        break;

      default:
        return false;
    }

    plugin.udm.displayStatus((Player) sender, allowedTypes(sender), allowedOptions(sender));
    sender.sendMessage(plugin.prepareMessage("messages.settings_saved"));

    return true;
  }

  // ----------------------------------------------------------------------------------------------
  // Custom private methods
  // ----------------------------------------------------------------------------------------------

  private List<String> allowedOptions(CommandSender sender) {
    List<String> list = new ArrayList<>();

    for (options option : SimpleCompassUsersDatas.options.values())
      if (sender.hasPermission("scompass.option." + option)) list.add(option.toString());

    return list;
  }

  private List<String> allowedTypes(CommandSender sender) {
    List<String> list = new ArrayList<>();

    for (types type : SimpleCompassUsersDatas.types.values())
      if (sender.hasPermission("scompass.use." + type)) list.add(type.toString());

    return list;
  }

  private boolean hasAccess(CommandSender sender, String[] args, boolean showError) {
    if (!(sender instanceof Player)) {
      if (showError) sender.sendMessage(plugin.prepareMessage("errors.command_only_for_players"));

      return false;
    }

    if (args.length > 2 || (allowedOptions(sender).size() < 1) || (allowedTypes(sender).size() < 1)) {
      if (showError) sender.sendMessage(plugin.prepareMessage("errors.wrong_usage"));

      return false;
    }

    if (args.length > 0 && !allowedOptions(sender).contains(args[0])) {
      if (showError)
        sender.sendMessage(plugin.prepareMessage("errors.invalid_option", ImmutableMap.of("option", args[0])));

      return false;
    }

    if (args.length > 1 && !allowedTypes(sender).contains(args[1])) {
      if (showError) sender.sendMessage(plugin.prepareMessage("errors.invalid_type", ImmutableMap.of("type", args[0])));

      return false;
    }

    return true;
  }
}
