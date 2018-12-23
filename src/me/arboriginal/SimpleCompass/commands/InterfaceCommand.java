package me.arboriginal.SimpleCompass.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassModes;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.NMSUtil;
import me.arboriginal.SimpleCompass.utils.OptionUtil;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class InterfaceCommand extends OptionUtil implements CommandExecutor {
  //-----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public InterfaceCommand(SimpleCompass main) {
    super(main, "scompass options");
  }

  // ----------------------------------------------------------------------------------------------
  // CommandExecutor methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player))
      plugin.sendMessage(sender, "command_only_for_players");
    else if (args.length == 0)
      showOptions((Player) sender, null);
    else if (args[0].equals("options"))
      return performCommandOption(sender, Arrays.stream(args).skip(1).toArray(String[]::new));

    return true;
  }

  // ----------------------------------------------------------------------------------------------
  // SimpleCompassOptions methods
  // ----------------------------------------------------------------------------------------------

  public void showOptions(Player player, CompassTypes modified) {
    if (modified != null) plugin.sendMessage(player, optKey + ".saved");

    ItemStack book = buildInterface(player, modified);

    if (!NMSUtil.openBook(player, book)) giveBook(player, book);
  }

  // ----------------------------------------------------------------------------------------------
  // Private methods
  // ----------------------------------------------------------------------------------------------

  private ItemStack buildInterface(Player player, CompassTypes modified) {
    ItemStack          book  = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta           meta  = (BookMeta) book.getItemMeta();
    List<String>       opts  = allowedOptions(player);
    List<CompassTypes> types = allowedTypes(player);

    if (modified != null && types.remove(modified))
      meta.spigot().addPage(BuildPage(player, modified, opts));

    for (CompassTypes type : types) meta.spigot().addPage(BuildPage(player, type, opts));

    book.setItemMeta(meta);

    return book;
  }

  private BaseComponent[] BuildPage(Player player, CompassTypes type, List<String> optionsList) {
    CompassModes                     typeMode = plugin.datas.getCompassMode(player, type);
    CompassOptions                   selected = plugin.datas.getCompassOption(player, type);
    ArrayList<BaseComponent>         content  = new ArrayList<BaseComponent>();
    Map<String, Map<String, String>> commands = new LinkedHashMap<String, Map<String, String>>();

    content.add(new TextComponent(plugin.prepareMessage(optKey + ".header")));

    for (CompassModes mode : CompassModes.values())
      commands.put("{" + mode + "}", clickableOption(type, mode, typeMode));

    content.add(plugin.createClickableMessage(plugin.prepareMessage(optKey + ".content",
        ImmutableMap.of("type", plugin.locale.getString("types." + type))), commands));

    content.add(new TextComponent("\n" + plugin.prepareMessage(optKey + ".footer") + "\n"));

    commands = new LinkedHashMap<String, Map<String, String>>();

    for (String option : optionsList) {
      if (option.equals(CompassModes.MODE180.toString()) || option.equals(CompassModes.MODE360.toString())) continue;

      commands.put("{" + option + "}", clickableOption(type, option, selected));
    }

    content.add(plugin.createClickableMessage(String.join("\n", commands.keySet()), commands));

    return content.stream().toArray(BaseComponent[]::new);
  }

  private void giveBook(Player player, ItemStack book) {
    if (!plugin.config.getBoolean("interface.give_book_on_fail")) {
      plugin.sendMessage(player, "interface_failed_auto_open");

      return;
    }

    long cooldown = plugin.datas.getBookCooldown(player);

    if (cooldown > 0) {
      plugin.sendMessage(player, "interface_book_give_cooldown", ImmutableMap.of("delay", "" + cooldown));

      return;
    }

    int slot = player.getInventory().firstEmpty();

    if (slot == -1) {
      plugin.sendMessage(player, "interface_failed_auto_open_give_failed");

      return;
    }

    player.getInventory().setItem(slot, book);
    plugin.datas.setBookCooldown(player);

    plugin.sendMessage(player, "interface_failed_auto_open_give");
  }
}
