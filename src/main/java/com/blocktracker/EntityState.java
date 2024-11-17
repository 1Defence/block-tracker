package com.blocktracker;

import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;

public class EntityState
{
    enum ENTITY_TYPE {NPC,PLAYER};
    protected WorldPoint lastLocation;
    protected Actor trackedActor;
    protected ENTITY_TYPE entity_type;
    protected int stuckTicks;

}
