package com.blocktracker;

import net.runelite.api.NPC;
import net.runelite.api.Actor;

public class NpcState extends EntityState
{
    public NpcState(NPC trackedNPC)
    {
        this.entity_type = ENTITY_TYPE.NPC;
        this.trackedActor = this.trackedNPC = trackedNPC;
        this.trackedIndex = trackedNPC.getIndex();
        this.lastLocation = trackedNPC.getWorldLocation();
        this.lastTarget = trackedNPC.getInteracting();
        this.stuckTicks = 0;
    }

    NPC trackedNPC;
    int trackedIndex;
    Actor lastTarget;

}
