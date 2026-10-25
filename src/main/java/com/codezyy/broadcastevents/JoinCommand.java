package com.codezyy.broadcastevents;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JoinCommand implements CommandExecutor, TabCompleter {
    private final BroadcastEvents plugin;

    public JoinCommand(BroadcastEvents plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
        Player p = (Player) sender;
        String err;
        if (cmd.getName().equalsIgnoreCase("leave")) {
            err = plugin.events().leave(p);
        } else {
            err = plugin.events().join(p, args.length > 0 ? args[0] : null);
        }
        if (err != null) plugin.reply(p, "&c" + err);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("join") && args.length == 1) {
            for (String n : plugin.events().runningNames())
                if (n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(n);
        }
        return out;
    }
}
