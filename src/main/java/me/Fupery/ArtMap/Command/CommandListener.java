package me.Fupery.ArtMap.Command;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.TitleFilter;
import me.Fupery.ArtMap.Utils.LocationTag;
import me.Fupery.ArtMap.Utils.Recipe;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

import static me.Fupery.ArtMap.Utils.Formatting.*;

public class CommandListener implements CommandExecutor {

    private ArtMap plugin;
    private HashMap<String, ArtMapCommand> commands;

    public CommandListener(final ArtMap plugin) {
        this.plugin = plugin;
        commands = new HashMap<>();

        commands.put("save", new ArtMapCommand("artmap.artist", 2, 2, "/artmap save <title>") {

            @Override
            public boolean runCommand(CommandSender sender, String[] args, ReturnMessage msg) {

                final String title = args[1];

                if (sender instanceof Player) {

                    final Player player = (Player) sender;

                    if (!new TitleFilter(plugin, title).check()) {
                        msg.message = playerError(badTitle);
                        return false;
                    }

                    MapArt art = MapArt.getArtwork(plugin, title);

                    if (art != null) {
                        msg.message = playerError(titleUsed);
                        return false;
                    }

                    if (plugin.getArtistHandler() != null
                            && plugin.getArtistHandler().containsPlayer(player)) {


                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override
                            public void run() {
                                Easel easel = null;

                                Entity seat = player.getVehicle();

                                if (seat != null) {

                                    if (seat.hasMetadata("easel")) {
                                        String tag = seat.getMetadata("easel").get(0).asString();
                                        Location location = LocationTag.getLocation(seat.getWorld(), tag);

                                        easel = plugin.getEasels().get(location);
                                    }
                                }

                                if (easel != null) {

                                    MapArt art = new MapArt(easel.getItem().getDurability(),
                                            title, player);

                                    //Makes sure that frame is empty before saving
                                    for (int i = 0; i < 3; i++) {

                                        easel.getFrame().setItem(new ItemStack(Material.AIR));

                                        if (!easel.hasItem()) {
                                            art.saveArtwork(plugin);
                                            easel.getFrame().setItem(new ItemStack(Material.AIR));
                                            plugin.getArtistHandler().removePlayer(player);
                                            ItemStack leftOver = player.getInventory().addItem(art.getMapItem()).get(0);

                                            if (leftOver != null) {
                                                player.getWorld().dropItemNaturally(player.getLocation(), leftOver);
                                            }
                                            player.sendMessage(playerMessage(String.format(saveSuccess, title)));
                                            return;
                                        }
                                    }
                                    player.sendMessage(playerError(unknownError));
                                    return;
                                }
                                player.sendMessage(playerError(notRidingEasel));
                            }
                        });
                        return true;

                    } else {
                        player.sendMessage(playerError(notRidingEasel));
                        return false;
                    }

                } else {
                    msg.message = playerError(noConsole);
                    return false;
                }
            }
        });

        commands.put("delete", new ArtMapCommand(null, 2, 2, "/artmap delete <title>") {
            @Override
            public boolean runCommand(CommandSender sender, String[] args, ReturnMessage msg) {

                MapArt art = MapArt.getArtwork(plugin, args[1]);

                if (art != null && sender instanceof Player
                        && !art.getPlayer().getName().equalsIgnoreCase(sender.getName())
                        && !sender.hasPermission("artmap.admin")) {
                    msg.message = playerMessage(noperm);
                    return false;
                }

                if (MapArt.deleteArtwork(plugin, args[1])) {
                    msg.message = playerMessage(String.format(deleted, args[1]));
                    return true;

                } else {
                    msg.message = playerError(String.format(mapNotFound, args[1]));
                    return false;
                }
            }
        });

        commands.put("preview", new ArtMapCommand(null, 2, 2, "/artmap preview <title>") {

            @Override
            public boolean runCommand(CommandSender sender, String[] args, ReturnMessage msg) {

                if (sender instanceof Player) {
                    Player player = (Player) sender;

                    if (player.getItemInHand().getType() == Material.AIR) {

                        MapArt art = MapArt.getArtwork(plugin, args[1]);

                        if (art != null) {

                            if (player.hasPermission("artmap.admin")) {

                                player.setItemInHand(art.getMapItem());

                            } else {
                                plugin.startPreviewing(((Player) sender), art);
                            }
                            msg.message = playerMessage(String.format(previewing, args[1]));
                            return true;

                        } else {
                            msg.message = playerError(String.format(mapNotFound, args[1]));
                        }

                    } else {
                        msg.message = playerMessage(emptyHandPreview);
                    }

                } else {
                    msg.message = playerError(noConsole);
                }
                return false;
            }
        });

        commands.put("list", new ArtMapCommand(null, 1, 3, "/artmap list [playername|all] [pg]") {
            @Override
            public boolean runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
                String artist;
                int pg = 1;

                //checks args are valid
                if (args.length > 1) {
                    artist = args[1];

                } else {
                    artist = (sender instanceof Player) ? sender.getName() : "all";
                }
                if (args.length == 3) {

                    for (char c : args[2].toCharArray()) {

                        if (!Character.isDigit(c)) {
                            msg.message = playerError(usage);
                            return false;
                        }
                    }
                    pg = Integer.parseInt(args[2]);
                }

                //fetches artworks by 'artist'
                String[] list = MapArt.listArtworks(plugin, artist.toLowerCase());

                //returns if no artworks found
                if (list == null || list.length == 0) {
                    msg.message = playerError(String.format(noArtworksFound, artist));
                    return false;
                }

                //index of the last page
                int totalPages = (list.length / 8) + 1;

                if (pg > totalPages) {
                    pg = totalPages;

                } else if (pg < 1) {
                    pg = 1;
                }

                MultiLineReturnMessage multiMsg = new MultiLineReturnMessage(sender,
                        String.format(listHeader, artist));

                TextComponent[] lines = new TextComponent[8];
                int msgIndex = (pg - 1) * 8;
                String title, cmd, hover;

                for (int i = 0; i < lines.length && (i + msgIndex) < list.length; i++) {
                    title = extractListTitle(list[i + msgIndex]);
                    cmd = String.format("/artmap preview %s", title);
                    hover = String.format(listLineHover, title);

                    lines[i] = new TextComponent(list[i + msgIndex]);
                    lines[i].setHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    new ComponentBuilder(hover).create()));
                    lines[i].setClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                }

                //footer shows current page number, footerButton links to next page
                TextComponent footer =
                        new TextComponent(String.format(listFooterPage, pg, totalPages));

                //attaches a clickable button to open the next page to the footer
                if (totalPages > pg) {
                    TextComponent footerButton = new TextComponent(listFooterButton);
                    cmd = String.format("/artmap list %s %s", artist, pg + 1);

                    footerButton.setClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                    footerButton.setHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    new ComponentBuilder(listFooterNxt).create()));
                    footer.addExtra(footerButton);
                }
                multiMsg.setLines(lines);
                multiMsg.setFooter(footer);

                //sends the list to the player
                Bukkit.getScheduler().runTask(plugin, multiMsg);
                return true;
            }
        });

        commands.put("help", new ArtMapCommand(null, 1, 1, "/artmap help") {
            @Override
            public boolean runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
                MultiLineReturnMessage multiMsg =
                        new MultiLineReturnMessage(sender, playerMessage(helpHeader));
                multiMsg.setLines(new String[]{
                        helpLine("/artmap save <title>", "save your artwork"),
                        helpLine("/artmap delete <title>", "delete your artwork"),
                        helpLine("/artmap preview <title>", "preview an artwork"),
                        helpLine("/artmap list [playername|all] [pg]", "list artworks"),
                        helpMessage
                });
                if (sender instanceof Player && sender.hasPermission("artmap.admin")) {

                    TextComponent footer = new TextComponent(ChatColor.AQUA + "Recipes: ");
                    TextComponent[] items = new TextComponent[Recipe.values().length];

                    for (int i = 0; i < items.length; i++) {
                        String title = Recipe.values()[i].name().toLowerCase();
                        items[i] = new TextComponent(String.format(recipeButton, title) +
                                ChatColor.GOLD + " | ");
                        items[i].setClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/artmap get " + title));
                        items[i].setHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        new ComponentBuilder(String.format(getItem, title)).create()));
                        footer.addExtra(items[i]);
                    }
                    multiMsg.setFooter(footer);
                }
                Bukkit.getScheduler().runTask(plugin, multiMsg);
                return true;
            }
        });

        commands.put("get", new ArtMapCommand("artmap.admin", 2, 2, "/artmap get <item>") {
            @Override
            public boolean runCommand(CommandSender sender, String[] args, ReturnMessage msg) {

                if (sender instanceof Player) {

                    Player player = (Player) sender;

                    for (Recipe recipe : Recipe.values()) {

                        if (args[1].equalsIgnoreCase(recipe.name())) {
                            ItemStack leftOver =
                                    player.getInventory().addItem(recipe.getResult()).get(0);
                        }
                    }

                } else {
                    msg.message = playerError(noConsole);
                }
                return false;
            }
        });
        for (ArtMapCommand command : commands.values()) {
            command.plugin = plugin;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length > 0) {

            if (commands.containsKey(args[0].toLowerCase())) {
                commands.get(args[0].toLowerCase()).runPlayerCommand(sender, args);
                return true;
            }
        }
        sender.sendMessage(playerError("/artmap help for a list of commands."));
        return true;
    }

    public HashMap<String, ArtMapCommand> getCommands() {
        return commands;
    }

    public ArtMap getPlugin() {
        return plugin;
    }
}