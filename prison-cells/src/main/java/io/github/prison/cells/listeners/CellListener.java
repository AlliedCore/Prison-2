/*
 *  Prison is a Minecraft plugin for the prison game mode.
 *  Copyright (C) 2016 The Prison Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.prison.cells.listeners;

import com.google.common.eventbus.Subscribe;
import io.github.prison.Prison;
import io.github.prison.cells.Cell;
import io.github.prison.cells.CellPermission;
import io.github.prison.cells.CellUser;
import io.github.prison.cells.CellsModule;
import io.github.prison.cells.events.CellDoorEvent;
import io.github.prison.internal.Player;
import io.github.prison.internal.events.BlockPlaceEvent;
import io.github.prison.internal.events.PlayerChatEvent;
import io.github.prison.internal.events.PlayerInteractEvent;
import io.github.prison.util.Block;
import io.github.prison.util.Location;

/**
 * @author SirFaizdat
 */
public class CellListener {

    private CellsModule cellsModule;

    public CellListener(CellsModule cellsModule) {
        this.cellsModule = cellsModule;
    }

    public void init() {
        Prison.getInstance().getEventBus().register(this);
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent e) {
        if (!cellsModule.getQueue().isQueued(e.getPlayer())) return;
        if (e.getMessage().toLowerCase().equals("cancel")) {
            e.setCanceled(true);
            cellsModule.getQueue().cancel(e.getPlayer());
            e.getPlayer().sendMessage(cellsModule.getMessages().creationCanceled);
        }
    }

    @Subscribe
    public void onPlayerHitDoor(PlayerInteractEvent e) {
        try {
            if (e.getAction() != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK)
                return; // Must be punch
            if (!cellsModule.getQueue().isQueued(e.getPlayer())) return; // Must be queued

            e.setCanceled(true);
            Location clickedBlockLoc = e.getClicked();
            Block clickedBlockType = e.getPlayer().getLocation().getWorld().getBlockAt(clickedBlockLoc);
            if (!Block.isDoor(clickedBlockType)) {
                e.getPlayer().sendMessage(cellsModule.getMessages().mustBeDoor);
                return; // Must be door
            }

            if (!isTopHalf(clickedBlockLoc)) clickedBlockLoc.setY(clickedBlockLoc.getY() + 1);

            Cell cell = cellsModule.getQueue().getQueuedCell(e.getPlayer());
            cell.setDoorLocation(clickedBlockLoc);
            cellsModule.getQueue().setQueuedCell(e.getPlayer(), cell);
            cellsModule.getQueue().complete(e.getPlayer()); // Creation complete.
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    // If the block below the door is not a door, then the door block is not the top half
    private boolean isTopHalf(Location loc) {
        Location locBelow = new Location(loc.getWorld(), loc.getX(), loc.getY() - 1, loc.getZ());
        Block blockBelow = loc.getWorld().getBlockAt(locBelow);
        return Block.isDoor(blockBelow);
    }

    @Subscribe
    public void onPlayerRightClickDoor(PlayerInteractEvent e) {
        try {
            if (e.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
                return; // Must be right-click
            if (cellsModule.getUser(e.getPlayer().getUUID()) == null) return; // Must be a cell user

            Location clickedBlockLoc = e.getClicked();
            Block clickedBlockType = e.getPlayer().getLocation().getWorld().getBlockAt(clickedBlockLoc);
            if (!Block.isDoor(clickedBlockType)) return; // Must be a door

            Cell cell = cellsModule.getCellByDoor(clickedBlockLoc);
            if (cell == null) {
                // Maybe this is the lower half
                clickedBlockLoc.setY(clickedBlockLoc.getBlockY() + 1);
                cell = cellsModule.getCellByDoor(clickedBlockLoc);

                if (cell == null) return; // Or maybe not
            }

            CellUser user = cellsModule.getUser(e.getPlayer().getUUID());
            if (!user.hasAccess(cell.getCellId()) ||
                    !user.getCellPermissions(cell.getCellId()).contains(CellPermission.CAN_ACCESS_DOOR)) {
                e.setCanceled(true);
                Prison.getInstance().getPlatform().showTitle(
                        e.getPlayer(), "Access Denied!",
                        "You're not allowed to open " + Prison.getInstance().getPlatform().getPlayer(cell.getOwner()).getName() + "'s cell door!",
                        2
                );
                return;
            }

            CellDoorEvent event = new CellDoorEvent(cell, e.getPlayer());
            Prison.getInstance().getEventBus().post(event);
            if (event.isCanceled()) return; // The event was canceled somewhere, don't open the door.

            // At this point, we've ensured the user can open the door.
            // If it's an iron door, we'll open/close for them
            Prison.getInstance().getPlatform().toggleDoor(clickedBlockLoc);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    @Subscribe
    public void onPlayerAttemptBuild(BlockPlaceEvent e) {
        Player placer = e.getPlayer();
        CellUser user = cellsModule.getUser(placer.getUUID());
        if (user == null) return;

        Location loc = e.getBlockLocation();
        Cell cell = cellsModule.getCellByLocation(loc);
        if (cell == null) return;

        if (!user.hasAccess(cell.getCellId()) || !user.getCellPermissions(cell.getCellId()).contains(CellPermission.BUILD)) {
            placer.sendMessage(String.format(cellsModule.getMessages().noPermission, CellPermission.BUILD.getUserFriendlyName()));
            e.setCanceled(true);
        }
    }

    @Subscribe
    public void onPlayerAttemptChest(PlayerInteractEvent e) {
        Player placer = e.getPlayer();
        CellUser user = cellsModule.getUser(placer.getUUID());
        if (user == null) return;

        Location loc = e.getClicked();
        if (e.getClicked().getWorld().getBlockAt(loc) != Block.CHEST) return; // Must be chest

        Cell cell = cellsModule.getCellByLocation(loc);
        if (cell == null) return;

        if (!user.hasAccess(cell.getCellId()) || !user.getCellPermissions(cell.getCellId()).contains(CellPermission.CAN_ACCESS_CHESTS)) {
            placer.sendMessage(String.format(cellsModule.getMessages().noPermission, CellPermission.CAN_ACCESS_CHESTS.getUserFriendlyName()));
            e.setCanceled(true);
        }
    }

}