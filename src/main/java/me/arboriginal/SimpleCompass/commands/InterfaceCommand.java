package me.arboriginal.SimpleCompass.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitRunnable;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker.TargetSelector;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker.TrackingActions;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.NMSUtil;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class InterfaceCommand extends AbstractCommand implements CommandExecutor, TabCompleter {
    private static final String SELECT_TARGET = "*S313C7:74R637*", SELECT_PAGER = "*S313C7:P463R*";

    // Constructor methods ---------------------------------------------------------------------------------------------

    public InterfaceCommand(SimpleCompass plugin) {
        super(plugin, "scompass");

        subCommands.put(SubCmds.OPTION, plugin.locale.getString("subcommands." + SubCmds.OPTION));

        if (!sc.trackers.isEmpty())
            subCommands.put(SubCmds.TRACK, plugin.locale.getString("subcommands." + SubCmds.TRACK));
    }

    // CommandExecutor methods -----------------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) sc.sendMessage(sender, "command_only_for_players");
        else if (((Player) sender).isSleeping()) {
            sc.sendMessage(sender, "command_no_sleeping");
            return true;
        }
        else if (args.length == 0) showOptions((Player) sender, null);
        else if (args[0].equals(SELECT_TARGET)) targetSelector((Player) sender, subArgs(args));
        else if (subCommand((Player) sender, SubCmds.OPTION, args[0]))
            return performCommandOption((Player) sender, subArgs(args));
        else if (subCommand((Player) sender, SubCmds.TRACK, args[0]))
            return performCommandTrack((Player) sender, subArgs(args));
        else {
            sc.sendMessage(sender, "wrong_usage");
            return false;
        }

        return true;
    }

    // TabCompleter methods --------------------------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !(sender instanceof Player)) return null;

        if (args.length == 1) {
            List<String> list = new ArrayList<String>();

            for (SubCmds subCommand : subCommands.keySet()) {
                String subCommandName = subCommands.get(subCommand);

                if (subCommandName.toLowerCase().startsWith(args[0].toLowerCase())) list.add(subCommandName);
            }

            return list;
        }
        else if (subCommand((Player) sender, SubCmds.OPTION, args[0]))
            return completeCommandOption((Player) sender, Arrays.stream(args).skip(1).toArray(String[]::new));
        else if (subCommand((Player) sender, SubCmds.TRACK, args[0]))
            return completeCommandTrack((Player) sender, Arrays.stream(args).skip(1).toArray(String[]::new));

        sc.sendMessage(sender, "wrong_usage");
        return null;
    }

    // CommandUtil methods ---------------------------------------------------------------------------------------------

    @Override
    public void showOptions(Player player, CompassTypes modified) {
        if (modified != null) sc.sendMessage(player, "commands." + mainCommand + ".saved");

        ItemStack book = buildInterface(player, modified);

        if (sc.config.getBoolean("interface.give_book_everytime") || !NMSUtil.openBook(player, book))
            giveBook(player, book);
    }

    // Private methods -------------------------------------------------------------------------------------------------

    private ItemStack buildInterface(Player player, CompassTypes modified) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta  meta = bookMeta(book, "options");

        List<CompassTypes> types = allowedTypes(player);
        List<String>       opts  = allowedOptions(player);

        if (modified != null) buildInterfaceOptions(player, meta, types, opts, modified);
        if (player.hasPermission("scompass.track")) buildInterfaceTracking(player, meta);
        if (modified == null) buildInterfaceOptions(player, meta, types, opts, modified);

        book.setItemMeta(meta);
        return book;
    }

    private void buildInterfaceOptions(
            Player player, BookMeta meta, List<CompassTypes> types, List<String> opts, CompassTypes modified) {
        if (!player.hasPermission("scompass.option")) return;

        if (modified != null) {
            types.remove(modified);
            meta.spigot().addPage(buildPage(player, modified, opts));
        }

        for (CompassTypes type : types) meta.spigot().addPage(buildPage(player, type, opts));
    }

    private void buildInterfaceTracking(Player player, BookMeta meta) {
        List<String> trackers = new ArrayList<String>();
        Set<String>  ordered  = sc.locale.getConfigurationSection(
                "commands." + mainCommand + ".track.buttons").getKeys(false);

        for (String trackerID : sc.targets.trackersPriority)
            if (sc.targets.canUseTracker(player, trackerID)) trackers.add(trackerID);

        for (String[] part : chunk(trackers.toArray(new String[trackers.size()]),
                sc.locale.getInt("commands." + mainCommand + ".track.per_page"))) {
            ArrayList<BaseComponent> content = new ArrayList<BaseComponent>();
            content.add(new TextComponent(sc.prepareMessage("commands." + mainCommand + ".header") + "\n\n"));

            for (String trackerID : part) {
                AbstractTracker tracker = sc.trackers.get(trackerID);
                if (tracker == null) continue;

                Map<String, Map<String, String>> commands = new LinkedHashMap<String, Map<String, String>>();
                List<TrackingActions>            actions  = tracker.getActionsAvailable(player, false);

                for (String actionName : ordered) {
                    TrackingActions action; // @formatter:off
                    try { action = TrackingActions.valueOf(actionName); } catch (Exception e) { continue; }
                    // @formatter:on
                    if (!actions.contains(action)) {
                        commands.put("{" + action + "}", ImmutableMap.of("text", sc.prepareMessage(
                                "commands." + mainCommand + ".track.buttons." + action + ".text_inactive")));
                        continue;
                    }

                    commands.put("{" + action + "}", ImmutableMap.of("text",
                            sc.prepareMessage("commands." + mainCommand + ".track.buttons." + action + ".text"),
                            "hover", sc.prepareMessage(
                                    "commands." + mainCommand + ".track.buttons." + action + ".hover"),
                            "click", "/" + mainCommand
                                    + " " + (tracker.requireTarget(action) != TargetSelector.NONE
                                            ? SELECT_TARGET
                                            : subCommands.get(SubCmds.TRACK))
                                    + " " + tracker.trackerName() + " " + tracker.getActionName(action)));
                }

                content.add(sc.createClickableMessage(sc.prepareMessage("commands." + mainCommand + ".track.content",
                        ImmutableMap.of("tracker", tracker.trackerName(), "buttons",
                                String.join(" ", commands.keySet())))
                        + "\n", commands));
            }

            meta.spigot().addPage(content.stream().toArray(BaseComponent[]::new));
        }
    }

    private BaseComponent[] buildPage(Player player, CompassTypes type, List<String> optionsList) {
        CompassModes                     typeMode = sc.datas.compassModeGet(player, type);
        CompassOptions                   selected = sc.datas.compassOptionGet(player, type);
        ArrayList<BaseComponent>         content  = new ArrayList<BaseComponent>();
        Map<String, Map<String, String>> commands = new LinkedHashMap<String, Map<String, String>>();

        content.add(new TextComponent(sc.prepareMessage("commands." + mainCommand + ".header")));

        for (CompassModes mode : CompassModes.values())
            commands.put("{" + mode + "}", clickableOption(type, mode, typeMode));

        content.add(sc.createClickableMessage(sc.prepareMessage("commands." + mainCommand + ".content",
                ImmutableMap.of("type", sc.locale.getString("types." + type))), commands));
        content.add(new TextComponent("\n" + sc.prepareMessage("commands." + mainCommand + ".footer") + "\n"));

        commands = new LinkedHashMap<String, Map<String, String>>();

        for (String option : optionsList) {
            if (option.equals(CompassModes.MODE180.toString()) || option.equals(CompassModes.MODE360.toString()))
                continue;

            commands.put("{" + option + "}", clickableOption(type, option, selected));
        }

        content.add(sc.createClickableMessage(String.join("\n", commands.keySet()), commands));

        return content.stream().toArray(BaseComponent[]::new);
    }

    private void giveBook(Player player, ItemStack book) {
        if (!sc.config.getBoolean("interface.give_book_everytime")
                && !sc.config.getBoolean("interface.give_book_on_fail")) {
            sc.sendMessage(player, "interface_failed_auto_open");
            return;
        }

        long cooldown = sc.datas.cooldownBookGet(player);

        if (cooldown > 0) {
            sc.sendMessage(player, "interface_book_give_cooldown",
                    ImmutableMap.of("delay", "" + sc.formatTime(cooldown)));
            return;
        }

        int slot = player.getInventory().firstEmpty();

        if (slot == -1) {
            sc.sendMessage(player, "interface_failed_auto_open_give_failed");
            return;
        }

        player.getInventory().setItem(slot, book);
        sc.datas.cooldownBookSet(player);
        sc.sendMessage(player, "interface_failed_auto_open_give");
    }

    private boolean subCommand(Player player, SubCmds command, String argument) {
        return subCommands.get(command) != null
                && argument.toLowerCase().equals(subCommands.get(command).toLowerCase())
                && player.hasPermission("scompass." + command.toString().toLowerCase())
                && (command == SubCmds.OPTION || !sc.trackers.isEmpty());
    }

    private void targetSelector(Player player, String[] args) {
        HashMap<String, Object> cmdArgs = getTrackArguments(player, args);
        if (cmdArgs.get("tracker") == null || cmdArgs.get("action") == null) return;

        AbstractTracker tracker = (AbstractTracker) cmdArgs.get("tracker");
        TrackingActions action  = (TrackingActions) cmdArgs.get("action");

        switch (tracker.requireTarget(action)) { // @formatter:off
            case ACTIVE:    targetSelectorList(player, tracker.activeTargets(player, ""), args); break;
            case AVAILABLE: targetSelectorList(player, tracker.availableTargets(player, ""), args); break;
            case NEW:       targetSelectorNew(player, args, false); break;
            case NEWCOORDS: targetSelectorNew(player, args, true); break;
            default:
        } // @formatter:on
    }

    private void targetSelectorFallback(Player player, List<BaseComponent[]> pages, String args[]) {
        int pager = 0;
        // @formatter:off
        if (args.length > 3 && args[2].equals(SELECT_PAGER))  
            try { pager = Integer.parseInt(args[3]); } catch (Exception e) {}
        // @formatter:on
        player.spigot().sendMessage(pages.get(pager));
        if (pages.size() == 1) return;

        Map<String, Map<String, String>> commands = new LinkedHashMap<String, Map<String, String>>();

        String command = "/" + mainCommand + " " + SELECT_TARGET + " " + args[0] + " " + args[1] + " " + SELECT_PAGER;

        if (pager > 0) commands.put("{prev}", ImmutableMap.of(
                "text", sc.prepareMessage("commands." + mainCommand + ".targets.prev.title"),
                "hover", sc.prepareMessage("commands." + mainCommand + ".targets.prev.hover"),
                "click", command + " " + (pager - 1)));

        if (pager < pages.size() - 1) commands.put("{next}", ImmutableMap.of(
                "text", sc.prepareMessage("commands." + mainCommand + ".targets.next.title"),
                "hover", sc.prepareMessage("commands." + mainCommand + ".targets.next.hover"),
                "click", command + " " + (pager + 1)));

        if (!commands.isEmpty()) player.spigot().sendMessage(
                sc.createClickableMessage("                " + String.join("   ", commands.keySet()) + "\n", commands));
    }

    private void targetSelectorList(Player player, List<String> targets, String[] args) {
        ItemStack book    = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta  meta    = bookMeta(book, "targets");
        String    head    = sc.prepareMessage("commands." + mainCommand + ".targets.header") + "\n\n";
        String    command = "/" + mainCommand + " " + subCommands.get(SubCmds.TRACK) + " " + String.join(" ", args);

        List<BaseComponent[]> pages = targetSelectorPages(targets, new TextComponent(head), command);
        for (BaseComponent[] page : pages) meta.spigot().addPage(page);
        book.setItemMeta(meta);

        if (!NMSUtil.openBook(player, book)) targetSelectorFallback(player, pages, args);
    }

    private List<BaseComponent[]> targetSelectorPages(List<String> targets, BaseComponent head, String command) {
        List<BaseComponent[]> pages = new ArrayList<BaseComponent[]>();

        for (String[] part : chunk(targets.toArray(new String[targets.size()]),
                sc.locale.getInt("commands." + mainCommand + ".targets.per_page"))) {
            BaseComponent[] page = new BaseComponent[part.length + 1];
            page[0] = head;

            for (int i = 0; i < part.length; i++) {
                Map<String, Map<String, String>> commands = new LinkedHashMap<String, Map<String, String>>();

                commands.put("{cmd}", ImmutableMap.of(
                        "text", sc.prepareMessage("commands." + mainCommand + ".targets.content",
                                ImmutableMap.of("target", part[i])),
                        "hover", sc.prepareMessage("commands." + mainCommand + ".targets.hover",
                                ImmutableMap.of("target", part[i])),
                        "click", command + " " + part[i]));

                page[i + 1] = sc.createClickableMessage("{cmd}" + "\n", commands);
            }

            pages.add(page);
        }

        if (pages.isEmpty()) pages.add(new BaseComponent[] { head,
                new TextComponent(sc.prepareMessage("commands." + mainCommand + ".targets.no_targets")) });

        return pages;
    }

    private void targetSelectorNew(Player player, String[] args, boolean coords) {
        String cancel = sc.locale.getString("commands." + mainCommand + ".targets.new.cancel");

        Conversation conv = new Conversation(sc, player, new StringPrompt() {
            @Override
            public String getPromptText(ConversationContext paramConversationContext) {
                return sc.prepareMessage(
                        "commands." + mainCommand + ".targets.new.name_" + (coords ? "coords" : "only"),
                        ImmutableMap.of("word", cancel));
            }

            @Override
            public Prompt acceptInput(ConversationContext paramConversationContext, String paramString) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (this.isCancelled()) return;
                        if (paramString.equals(cancel))
                            player.sendMessage(sc.prepareMessage("commands." + mainCommand + ".targets.new.cancelled"));
                        else player.performCommand(mainCommand + " " + subCommands.get(SubCmds.TRACK)
                                + " " + String.join(" ", args) + " " + paramString);
                        this.cancel();
                    }
                }.runTaskLater(sc, sc.config.getInt("delays.target_cancel"));

                return END_OF_CONVERSATION;
            }
        });

        conv.setLocalEchoEnabled(false);
        player.beginConversation(conv);
    }

    // ----------------------------------------------------------------------------------------------
    // Utils methods
    // ----------------------------------------------------------------------------------------------

    private BookMeta bookMeta(ItemStack book, String type) {
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(sc.prepareMessage("commands." + mainCommand + ".books." + type + ".title"));
        meta.setAuthor(sc.prepareMessage("commands." + mainCommand + ".books." + type + ".author"));

        ArrayList<String> lore = new ArrayList<String>();
        for (String row : sc.locale.getStringList("commands." + mainCommand + ".books." + type + ".lore"))
            lore.add(sc.formatMessage(row));

        meta.setLore(lore);
        return meta;
    }

    private List<String[]> chunk(String[] input, int number) {
        List<String[]> parts = new ArrayList<String[]>();

        int left = input.length;
        while (left > 0) {
            int      size = Math.min(left, number);
            String[] part = new String[size];

            System.arraycopy(input, input.length - left, part, 0, size);
            parts.add(part);

            left -= size;
        }

        return parts;
    }

    private String[] subArgs(String[] args) {
        return Arrays.stream(args).skip(1).toArray(String[]::new);
    }
}
