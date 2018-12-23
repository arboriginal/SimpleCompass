package me.arboriginal.SimpleCompass.utils;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public abstract class OptionUtil {
  protected SimpleCompass plugin;
  protected String        optCmd, optKey;

  public enum CompassOptions {
    ALWAYS, ELYTRA, ELYTRA_VEHICLE, VEHICLE, DISABLED,
  }

  //-----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public OptionUtil(SimpleCompass main, String command) {
    plugin = main;
    optCmd = command;
    optKey = "commands." + command.split(" ")[0];
  }

  // ----------------------------------------------------------------------------------------------
  // Protected methods
  // ----------------------------------------------------------------------------------------------

  protected List<String> allowedOptions(CommandSender sender) {
    List<String> list = new ArrayList<>();

    for (CompassOptions option : CompassOptions.values())
      if (sender.hasPermission("scompass.option." + option)) list.add(option.toString());

    for (CompassModes mode : CompassModes.values()) list.add(mode.toString());

    return list;
  }

  protected List<CompassTypes> allowedTypes(CommandSender sender) {
    List<CompassTypes> list = new ArrayList<CompassTypes>();

    for (CompassTypes type : CompassTypes.values()) {
      if (sender.hasPermission("scompass.use." + type)) list.add(type);
    }

    return list;
  }

  protected ImmutableMap<String, String> clickableOption(CompassTypes type, Object option, Object selected) {
    String optionType = (option instanceof CompassModes) ? "modes" : "options";
    String optionName = plugin.locale.getString(optionType + "." + option);

    return ImmutableMap.of(
        "text", plugin.prepareMessage(optKey + ".options."
            + (option.toString().equals(selected.toString()) ? "active" : "inactive"),
            ImmutableMap.of("option", optionName)),
        "hover", plugin.prepareMessage(optKey + ".options.hover",
            ImmutableMap.of("option", optionName, "type", plugin.locale.getString("types." + type))),
        "click", "/" + optCmd + " " + option + " " + type);
  }

  protected boolean hasAccessOption(CommandSender sender, String[] args, boolean showError) {
    if (args.length > 2 || allowedOptions(sender).isEmpty() || allowedTypes(sender).isEmpty()) {
      if (showError) plugin.sendMessage(sender, "wrong_usage");

      return false;
    }

    if (args.length > 0 && !allowedOptions(sender).contains(args[0])) {
      if (showError)
        plugin.sendMessage(sender, "invalid_option", ImmutableMap.of("option", args[0]));

      return false;
    }

    if (args.length > 1 && !isAllowedTypes(sender, args[1])) {
      if (showError) plugin.sendMessage(sender, "invalid_type", ImmutableMap.of("type", args[0]));

      return false;
    }

    return true;
  }

  protected boolean isAllowedTypes(CommandSender sender, String value) {
    for (CompassTypes type : allowedTypes(sender))
      if (type.toString().equalsIgnoreCase(value)) return true;

    return false;
  }

  protected CompassTypes modifyOption(Player player, CompassTypes type, String value) {
    if (value.equals(CompassModes.MODE180.toString()) || value.equals(CompassModes.MODE360.toString()))
      plugin.datas.setCompassMode(player, type, CompassModes.valueOf(value));
    else
      plugin.datas.setCompassOption(player, type, CompassOptions.valueOf(value));
    return type;
  }

  protected boolean performCommandOption(CommandSender sender, String[] args) {
    if (!hasAccessOption(sender, args, true)) return false;

    CompassTypes modified = null;

    switch (args.length) {
      case 0:
        break;

      case 1:
        if (allowedOptions(sender).size() != 1) {
          plugin.sendMessage(sender, "missing_type");
          return false;
        }

        modified = modifyOption((Player) sender, CompassTypes.valueOf(args[1]), allowedOptions(sender).get(0));
        break;

      case 2:
        modified = modifyOption((Player) sender, CompassTypes.valueOf(args[1]), args[0]);
        break;

      default:
        return false;
    }

    showOptions((Player) sender, modified);

    return true;
  }

  protected void showOptions(Player player, CompassTypes modified) {}
}
