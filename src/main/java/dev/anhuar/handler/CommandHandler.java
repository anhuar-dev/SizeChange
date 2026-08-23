package dev.anhuar.handler;

import dev.anhuar.SizeChange;
import dev.anhuar.command.SizeCommand;

public class CommandHandler {

    private final SizeCommand sizeCommand;

    public CommandHandler(SizeChange plugin) {
        this.sizeCommand = new SizeCommand(plugin);
        registerCommands();
    }

    private void registerCommands() {
        sizeCommand.register();
    }

    public SizeCommand getSizeCommand() {
        return sizeCommand;
    }
}
