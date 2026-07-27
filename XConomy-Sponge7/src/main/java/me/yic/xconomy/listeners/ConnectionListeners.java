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
import me.yic.xconomy.adapter.comp.CPlayer;
import me.yic.xconomy.lang.MessagesManager;
import me.yic.xconomy.task.Updater;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;

import java.net.MalformedURLException;
import java.net.URL;

public class ConnectionListeners {

    @SuppressWarnings("unused")
    @Listener
    public void onQuit(ClientConnectionEvent.Disconnect event) {
        CPlayer a = new CPlayer(event.getTargetEntity());
        PlayerConnection.onQuit(a);
    }

    @SuppressWarnings("unused")
    @Listener
    public void onJoin(ClientConnectionEvent.Join event) {
        CPlayer a = new CPlayer(event.getTargetEntity());

        PlayerConnection.onJoin(a);

        if (a.hasPermission("xconomy.admin.op")) {
            notifyUpdate(a, event.getTargetEntity());
        }

    }


    private void notifyUpdate(CPlayer messageRecipient, Player player) {
        if (!(XConomyLoad.Config.CHECK_UPDATE & Updater.old)) {
            return;
        }
        messageRecipient.sendMessage("§f[XConomy]§b" + MessagesManager.systemMessage("发现新版本 ") + Updater.newVersion);
        try {
            player.sendMessage(Text.builder(Updater.downloadUrl)
                    .onClick(TextActions.openUrl(new URL(Updater.downloadUrl)))
                    .build());
        } catch (MalformedURLException exception) {
            messageRecipient.sendMessage(Updater.downloadUrl);
        }
    }
}
