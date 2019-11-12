package me.arboriginal.SimpleCompass.commands;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class TrackCommand extends AbstractCommand implements CommandExecutor, TabCompleter {
    // Constructor methods ---------------------------------------------------------------------------------------------

    public TrackCommand(SimpleCompass plugin) {
        super(plugin, "sctrack");
    }

    // CommandExecutor methods -----------------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sc.sendMessage(sender, "command_only_for_players");
            return true;
        }

        if (((Player) sender).isSleeping()) {
            sc.sendMessage(sender, "command_no_sleeping");
            return true;
        }

        return performCommandTrack((Player) sender, args);
    }

    // TabCompleter methods --------------------------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) return completeCommandTrack((Player) sender, args);
        return null;
    }

    // CommandUtil methods ---------------------------------------------------------------------------------------------

    @Override
    public void showOptions(Player player, CompassTypes modified) {}
}
