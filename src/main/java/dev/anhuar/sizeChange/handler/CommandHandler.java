package dev.anhuar.sizeChange.handler;

/*
 * ========================================================
 * SizeChange - CommandHandler.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */

import dev.anhuar.sizeChange.SizeChange;
import dev.anhuar.sizeChange.command.AdminCommand;
import dev.anhuar.sizeChange.command.SizeCommand;
import revxrsal.commands.bukkit.BukkitCommandHandler;

public class CommandHandler {

    private final SizeChange plugin;
    private final BukkitCommandHandler commandHandler;

    public CommandHandler(SizeChange plugin) {
        this.plugin = plugin;
        this.commandHandler = BukkitCommandHandler.create(plugin);
        commandHandler.registerDependency(SizeChange.class, plugin);

        registerCommands();
    }

    private void registerCommands() {
        commandHandler.register(new AdminCommand(plugin));
        commandHandler.register(new SizeCommand(plugin));
    }
}