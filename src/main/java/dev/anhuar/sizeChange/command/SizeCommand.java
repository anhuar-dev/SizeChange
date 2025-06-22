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
            sender.sendRichMessage("<red>Jugador no encontrado. El jugador debe estar conectado.</red>");
            return;
        }

        if (size <= 0 || size > 10) {
            sender.sendRichMessage("<red>El tamaño debe estar entre 0.1 y 10.</red>");
            return;
        }

        boolean success = plugin.getManagerHandler().getSizeManager().setSize(target.getUniqueId(), size);

        if (success) {
            sender.sendRichMessage("<green>Tamaño de " + playerName + " establecido a " + size + ".</green>");
        } else {
            sender.sendRichMessage("<red>No se pudo establecer el tamaño para " + playerName + ".</red>");
        }
    }

    @Subcommand("size reset")
    public void resetSizeCommand(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendRichMessage("<red>Jugador no encontrado. El jugador debe estar conectado.</red>");
            return;
        }

        boolean success = plugin.getManagerHandler().getSizeManager().resetSize(target.getUniqueId());

        if (success) {
            sender.sendRichMessage("<green>Tamaño de " + playerName + " restablecido al valor predeterminado.</green>");
        } else {
            sender.sendRichMessage("<red>No se pudo restablecer el tamaño para " + playerName + ".</red>");
        }
    }

    @Subcommand("reload")
    public void reloadCommand(CommandSender sender) {
        plugin.getMessage().reload();
        plugin.getSetting().reload();
        sender.sendRichMessage("<green>SizeChange ha sido recargado correctamente!</green>");
    }
}