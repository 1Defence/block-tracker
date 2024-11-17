package com.blocktracker;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;

import java.awt.*;

import java.util.Map;

import static com.blocktracker.EntityState.ENTITY_TYPE;

public class TileOverlay extends Overlay
{
    private static final int MAX_DRAW_DISTANCE = 32;

    private final Client client;
    private final BlockTrackerPlugin plugin;

    @Inject
    private TileOverlay(Client client, BlockTrackerPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {

        for (Map.Entry<WorldPoint, EntityState> entry : plugin.tileMapping.entrySet())
        {

            //skip all checks, as no tracked npcs are present and the user only wants to see tracking conditionally
            if(plugin.configShowTrackingConditionally && plugin.trackedNpcMapping.isEmpty())
                continue;

            WorldPoint point = entry.getKey();
            EntityState state = entry.getValue();

            if(state.entity_type == ENTITY_TYPE.PLAYER)
            {
                //dont show players
                if (!plugin.configShowPlayerBlocking)
                    continue;

                //dont show local player
                if (!plugin.configShowLocalPlayerBlocking && plugin.getLocalPlayer() != null && plugin.getLocalPlayer().equals(state.trackedActor))
                    continue;
            }

            //npc can't reach target, consider it to be in a stuck status
            boolean tileIsStuck = plugin.configStuckTicks != 0 && state.stuckTicks >= plugin.configStuckTicks;

            //render blocked tile
            drawTile(graphics, point, tileIsStuck ? plugin.configStuckTileColor : plugin.configBlockedTileColor);
        }

        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color)
    {
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        if (point.distanceTo(playerLocation) >= MAX_DRAW_DISTANCE)
        {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null)
        {
            OverlayUtil.renderPolygon(graphics, poly, color, color, new BasicStroke((float) plugin.configBorderWidth));
        }
    }
}