package me.arboriginal.SimpleCompass.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker.TrackingActions;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public abstract class AbstractCommand {
    protected SimpleCompass        sc;
    protected String               mainCommand;
    protected Map<SubCmds, String> subCommands = new HashMap<SubCmds, String>();

    public enum SubCmds {
        OPTION, TRACK,
    }

    public enum CompassOptions {
        ALWAYS, ELYTRA, ELYTRA_VEHICLE, VEHICLE, DISABLED,
    }

    // Constructor methods ---------------------------------------------------------------------------------------------

    public AbstractCommand(SimpleCompass plugin, String command) {
        sc          = plugin;
        mainCommand = command;
    }

    // Abstract methods ------------------------------------------------------------------------------------------------

    protected abstract void showOptions(Player player, CompassTypes modified);

    // Public methods > Options ----------------------------------------------------------------------------------------

    public CompassTypes modifyOption(Player player, CompassTypes type, String value) {
        if (value.equals(CompassModes.MODE180.toString()) || value.equals(CompassModes.MODE360.toString()))
            sc.datas.compassModeSet(player, type, CompassModes.valueOf(value));
        else
            sc.datas.compassOptionSet(player, type, CompassOptions.valueOf(value));
        return type;
    }

    public boolean performCommandOption(Player player, String[] args) {
        if (!hasAccessOption(player, args, true)) return false;

        CompassTypes modified = null;

        switch (args.length) {
            case 0:
                break;

            case 1:
                if (allowedOptions(player).size() != 1) {
                    sc.sendMessage(player, "missing_type");
                    return false;
                }

                modified = modifyOption(player, CompassTypes.valueOf(args[1]), allowedOptions(player).get(0));
                break;

            case 2:
                modified = modifyOption(player, CompassTypes.valueOf(args[1]), args[0]);
                break;

            default:
                return false;
        }

        showOptions(player, modified);

        return true;
    }

    // Protected methods > Options -------------------------------------------------------------------------------------

    protected List<String> completeCommandOption(Player player, String[] args) {
        switch (args.length) {
            case 1:
                return getFilteredList(allowedOptions(player), args[0]);

            case 2:
                if (allowedOptions(player).contains(args[0])) return getFilteredList(allowedTypes(player), args[1]);
        }

        return null;
    }

    protected List<String> allowedOptions(Player player) {
        List<String> list = new ArrayList<>();

        for (CompassOptions option : CompassOptions.values())
            if (player.hasPermission("scompass.option." + option)) list.add(option.toString());

        for (CompassModes mode : CompassModes.values()) list.add(mode.toString());

        return list;
    }

    protected List<CompassTypes> allowedTypes(Player player) {
        List<CompassTypes> list = new ArrayList<CompassTypes>();

        for (CompassTypes type : CompassTypes.values())
            if (player.hasPermission("scompass.use." + type)) list.add(type);

        return list;
    }

    protected boolean hasAccessOption(Player player, String[] args, boolean showError) {
        if (args.length > 2 || allowedOptions(player).isEmpty() || allowedTypes(player).isEmpty()) {
            if (showError) sc.sendMessage(player, "wrong_usage");
            return false;
        }

        if (args.length > 0 && !allowedOptions(player).contains(args[0])) {
            if (showError) sc.sendMessage(player, "invalid_option", ImmutableMap.of("option", args[0]));
            return false;
        }

        if (args.length > 1 && !isAllowedTypes(player, args[1])) {
            if (showError) sc.sendMessage(player, "invalid_type", ImmutableMap.of("type", args[0]));
            return false;
        }

        return true;
    }

    protected boolean isAllowedTypes(Player player, String value) {
        for (CompassTypes type : allowedTypes(player)) if (type.toString().equalsIgnoreCase(value)) return true;

        return false;
    }

    // Public methods > Trackers ---------------------------------------------------------------------------------------

    public boolean performCommandTrack(Player player, String[] args) {
        HashMap<String, Object> cmdArgs = getTrackArguments(player, args);
        AbstractTracker         tracker = (AbstractTracker) cmdArgs.get("tracker");
        TrackingActions         action  = (TrackingActions) cmdArgs.get("action");
        String                  target  = (String) cmdArgs.get("target");

        if (tracker == null) {
            sc.sendMessage(player, "wrong_usage");
            return false;
        }

        if (action == null) {
            List<String> list = tracker.list(player, null, "");

            if (list == null || list.isEmpty()) tracker.sendMessage(player, "list_empty");
            else tracker.sendMessage(player, "list", ImmutableMap.of("list", String.join(", ", list)));
            return true;
        }

        if (action == TrackingActions.HELP && args.length == 2 && player.hasPermission("scompass.help")) {
            String help = tracker.help(player,
                    mainCommand + (subCommands.containsKey(SubCmds.TRACK) ? " " + subCommands.get(SubCmds.TRACK) : ""));

            if (help != null && !help.isEmpty()) player.sendMessage(help);
            return true;
        }

        return tracker.perform(player, "sctrack", action, target, args);
    }

    // Protected methods > Trackers ------------------------------------------------------------------------------------

    protected List<String> completeCommandTrack(Player player, String[] args) {
        if (args.length == 1) return sc.targets.getTrackersList((Player) player, args[0]);

        HashMap<String, Object> parsed  = getTrackArguments((Player) player, args);
        AbstractTracker         tracker = (AbstractTracker) parsed.get("tracker");
        if (tracker != null) return tracker.commandSuggestions((Player) player, args, parsed);

        return null;
    }

    protected List<TrackingActions> getActionsAvailable(Player player, String trackerID) {
        if (!sc.targets.canUseTracker(player, trackerID)) return null;
        return sc.trackers.get(trackerID).getActionsAvailable(player, false);
    }

    protected HashMap<String, Object> getTrackArguments(Player player, String[] args) {
        HashMap<String, Object> parsed = new HashMap<String, Object>();
        if (args.length == 0) return parsed;

        AbstractTracker tracker = sc.targets.getTrackerByName(args[0]);
        if (tracker == null || !sc.targets.getAvailableTrackers(player).contains(tracker.trackerID()))
            return parsed;

        parsed.put("tracker", tracker);
        if (args.length == 1) return parsed;

        tracker.parseArguments(player, args, parsed);
        return parsed;
    }

    // Utils methods ---------------------------------------------------------------------------------------------------

    protected ImmutableMap<String, String> clickableOption(CompassTypes type, Object option, Object selected) {
        String optionType = (option instanceof CompassModes) ? "modes" : "options";
        String optionName = sc.locale.getString(optionType + "." + option);

        return ImmutableMap.of(
                "text", sc.prepareMessage("commands." + mainCommand + ".options."
                        + (option.toString().equals(selected.toString()) ? "active" : "inactive"),
                        ImmutableMap.of("option", optionName)),
                "hover", sc.prepareMessage("commands." + mainCommand + ".options.hover",
                        ImmutableMap.of("option", optionName, "type", sc.locale.getString("types." + type))),
                "click", "/" + mainCommand
                        + (subCommands.containsKey(SubCmds.OPTION) ? " " + subCommands.get(SubCmds.OPTION) : "")
                        + " " + option + " " + type);
    }

    protected List<String> getFilteredList(List<?> inputList, String startWith) {
        List<String> list = new ArrayList<String>();

        for (Object candidate : inputList)
            if (candidate.toString().toLowerCase().startsWith(startWith.toLowerCase())) list.add(candidate.toString());

        return list;
    }
}
