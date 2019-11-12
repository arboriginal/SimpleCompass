package me.arboriginal.SimpleCompass.commands;

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

public class OptionCommand extends AbstractCommand implements CommandExecutor, TabCompleter {
    // Constructor methods ---------------------------------------------------------------------------------------------

    public OptionCommand(SimpleCompass plugin) {
        super(plugin, "scoption");
    }

    // CommandExecutor methods -----------------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sc.sendMessage(sender, "command_only_for_players");
            return true;
        }
        else if (((Player) sender).isSleeping()) {
            sc.sendMessage(sender, "command_no_sleeping");
            return true;
        }

        return performCommandOption((Player) sender, args);
    }

    // TabCompleter methods --------------------------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) return completeCommandOption((Player) sender, args);
        return null;
    }

    // SimpleCompassOptions methods ------------------------------------------------------------------------------------

    @Override
    public void showOptions(Player player, CompassTypes modified) {
        sc.sendMessage(player, "commands." + mainCommand + ".header");

        for (CompassTypes type : allowedTypes(player)) {
            CompassModes                     typeMode = sc.datas.compassModeGet(player, type);
            CompassOptions                   selected = sc.datas.compassOptionGet(player, type);
            Map<String, Map<String, String>> commands = new LinkedHashMap<String, Map<String, String>>();

            for (CompassModes mode : CompassModes.values())
                commands.put("{" + mode + "}", clickableOption(type, mode, typeMode));

            player.spigot().sendMessage(
                    sc.createClickableMessage(sc.prepareMessage("commands." + mainCommand + ".content",
                            ImmutableMap.of("type", sc.locale.getString("types." + type))), commands));

            commands = new LinkedHashMap<String, Map<String, String>>();

            for (String option : allowedOptions(player)) {
                if (option.equals(CompassModes.MODE180.toString()) || option.equals(CompassModes.MODE360.toString()))
                    continue;

                commands.put("{" + option + "}", clickableOption(type, option, selected));
            }

            player.spigot().sendMessage(sc.createClickableMessage(String.join("", commands.keySet()), commands));
        }

        sc.sendMessage(player, "commands." + mainCommand + ".footer");

        if (modified != null) sc.sendMessage(player, "commands." + mainCommand + ".saved");
    }
}
