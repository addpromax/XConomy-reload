/*
 *  This file (ConnectionListeners.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package me.yic.xconomy.listeners;

import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CChat;
import me.yic.xconomy.adapter.comp.CPlayer;
import me.yic.xconomy.lang.MessagesManager;
import me.yic.xconomy.task.Updater;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListeners implements Listener {

    @SuppressWarnings("unused")
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CPlayer a = new CPlayer(event.getPlayer());
        PlayerConnection.onQuit(a);
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        CPlayer a = new CPlayer(event.getPlayer());
        PlayerConnection.onJoin(a);

        if (a.isOp() || a.hasPermission("xconomy.admin.op")) {
            notifyUpdate(event.getPlayer());
        }
    }


    private void notifyUpdate(Player player) {
        if (!(XConomyLoad.Config.CHECK_UPDATE & Updater.old)) {
            return;
        }
        player.sendMessage(CChat.toLegacy("<white>[XConomy]<aqua>"
                + MessagesManager.systemMessage("found-version") + Updater.newVersion));
        TextComponent downloadLink = new TextComponent(Updater.downloadUrl);
        downloadLink.setColor(ChatColor.GREEN);
        downloadLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Updater.downloadUrl));
        player.spigot().sendMessage(downloadLink);

    }

}
