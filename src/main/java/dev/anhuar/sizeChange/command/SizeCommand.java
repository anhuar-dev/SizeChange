package dev.anhuar.sizeChange.command;

import dev.anhuar.sizeChange.SizeChange;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

/*
 * ========================================================
 * SizeChange - SizeCommand.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */

@Command("sizechange")
@CommandPermission("sizechange.admin")
public class SizeCommand {

    private final SizeChange plugin;

    public SizeCommand(SizeChange plugin) {
        this.plugin = plugin;
    }

    @Subcommand("size set")
    public void setSizeCommand(CommandSender sender, String playerName, float size) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendRichMessage(plugin.getMessage().getString("ERROR.OFFLINE"));
            return;
        }

        if (size <= 0 || size > 10) {
            sender.sendRichMessage(plugin.getMessage().getString("ERROR.INVALID-SIZE"));
            return;
        }

        boolean success = plugin.getManagerHandler().getSizeManager().setSize(target.getUniqueId(), size);

        if (success) {
            sender.sendRichMessage(plugin.getMessage().getString("SUCCESS.SET-SIZE").replace("%size%", String.valueOf(size).replace("%player%", target.getName())));
        } else {
            sender.sendRichMessage(plugin.getMessage().getString("ERROR.SET-SIZE-FAILED").replace("%player%", target.getName()));
        }
    }

    @Subcommand("size reset")
    public void resetSizeCommand(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendRichMessage(plugin.getMessage().getString("ERROR.OFFLINE"));
            return;
        }

        boolean success = plugin.getManagerHandler().getSizeManager().resetSize(target.getUniqueId());

        if (success) {
            sender.sendRichMessage(plugin.getMessage().getString("SUCCESS.RESET-SIZE").replace("%player%", target.getName()));
        } else {
            sender.sendRichMessage(plugin.getMessage().getString("ERROR.RESET-SIZE-FAILED").replace("%player%", target.getName()));
        }
    }

    @Subcommand("reload")
    public void reloadCommand(CommandSender sender) {
        plugin.getMessage().reload();
        plugin.getSetting().reload();
        sender.sendRichMessage("<green>SizeChange ha sido recargado correctamente!</green>");
    }
}