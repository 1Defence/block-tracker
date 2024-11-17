package com.blocktracker;

import net.runelite.api.Player;

public class PlayerState extends EntityState
{
    public PlayerState(Player trackedPlayer)
    {
        this.entity_type = ENTITY_TYPE.PLAYER;
        this.trackedActor = this.trackedPlayer = trackedPlayer;
        this.lastLocation = trackedPlayer.getWorldLocation();
        this.stuckTicks = 0;
    }

    Player trackedPlayer;

}