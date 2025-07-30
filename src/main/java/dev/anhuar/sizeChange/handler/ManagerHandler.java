package dev.anhuar.sizeChange.handler;

/*
 * ========================================================
 * SizeChange - ManagerHandler.java
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
import dev.anhuar.sizeChange.manager.SizeManager;
import lombok.Getter;

@Getter
public class ManagerHandler {

    private final SizeChange plugin;

    private SizeManager sizeManager;

    public ManagerHandler(SizeChange plugin) {
        this.plugin = plugin;
        registerManager();
    }

    public void registerManager() {
        this.sizeManager = new SizeManager(plugin);
    }
}