package com.blocktracker;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;

import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.GameTick;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.awt.Color;

@Slf4j
@PluginDescriptor(
	name = "Block Tracker",
	description = "Track tile blocking status of npcs and players"
)
public class BlockTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BlockTrackerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TileOverlay overlay;

	Map<Integer, NpcState> trackedNpcMapping;
	Map<Integer, PlayerState> trackedPlayerMapping;
	Map<WorldPoint, EntityState> tileMapping;

	@Getter(AccessLevel.PACKAGE)
	private List<String> trackableNpcs = new ArrayList<>();

	@Getter(AccessLevel.PACKAGE)
	private Actor localPlayer;

	Color configBlockedTileColor,
			configStuckTileColor;

	double configBorderWidth;

	int configStuckTicks;
	String configTrackedNpcs;

	boolean configShowPlayerBlocking,
			configShowLocalPlayerBlocking,
			configShowTrackingConditionally;

	@Override
	protected void startUp() throws Exception
	{
		generateInitialBlocking();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
	}

	/**
	 * Initial Generation of blocking based on present NPCS and players
	 * Initial blocking is not perfect as prior tracking is required to determine tile status.
	 * Also set on "Tracked Npcs" config change, as for processing reasons npcs are not needlessly tracked unless desired.
	 */
	public void generateInitialBlocking()
	{
		CacheConfigs();
		trackableNpcs = parseTrackableNpcNames();

		tileMapping = new HashMap<>();
		trackedNpcMapping = new TreeMap<>();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			TrackNpc(npc);
		}
		trackedPlayerMapping = new TreeMap<>();
		for (Player player : client.getTopLevelWorldView().players())
		{
			TrackPlayer(player);
		}
	}

	/**
	 * Convert player provided list of npc names into a checkable list
	 * These will be the only npcs tracked, unless no list is present; in such case all npcs are tracked(not recommended)
	 */
	public List<String> parseTrackableNpcNames()
	{
		final String configTrackedNpcsLower = configTrackedNpcs.toLowerCase();

		if (configTrackedNpcsLower.isEmpty())
		{
			return Collections.emptyList();
		}

		return Text.fromCSV(configTrackedNpcsLower);
	}

	/**
	 * Adds spawned player to a list for future tracking of movement change
	 */
	public void TrackPlayer(Player player)
	{
		if (player == null)
			return;
		PlayerState playerState = new PlayerState(player);
		trackedPlayerMapping.put(player.getId(), playerState);
		ModifyBlockingGeneric(playerState, player.getWorldLocation(),true);
		if (client.getLocalPlayer() != null && player == client.getLocalPlayer())
		{
			localPlayer = player;
		}
	}

	/**
	 * Removes despawned player from tracking
	 */
	public void RemovePlayerTracking(Player player)
	{
		ModifyBlockingGeneric(null, player.getWorldLocation(),false);
		trackedPlayerMapping.remove(player.getId());
	}


	/**
	 * Adds spawned npc to a list for future tracking of movement change
	 */
	public void TrackNpc(NPC npc)
	{
		if (npc == null)
			return;

		NPCComposition composition = npc.getComposition();

		if (composition == null)
			return;
		if (composition.isFollower())
			return;

		String name = composition.getName().toLowerCase();

		if (!trackableNpcs.isEmpty() && !trackableNpcs.contains(name))
			return;

		NpcState npcState = new NpcState(npc);
		trackedNpcMapping.put(npc.getIndex(), npcState);
		ModifyBlockingNpc(npcState,npc.getWorldLocation(),true);
	}

	/**
	 * Removes despawned npc from tracking
	 */
	public void RemoveNpcTracking(NPC npc)
	{
		int npcIndex = npc.getIndex();
		ModifyBlockingNpc(trackedNpcMapping.get(npcIndex),npc.getWorldLocation(),false);
		trackedNpcMapping.remove(npc.getIndex());
	}


	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		Player spawnedPlayer = event.getPlayer();
		if (spawnedPlayer == null)
			return;
		TrackPlayer(spawnedPlayer);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		Player despawnedPlayer = event.getPlayer();
		if (despawnedPlayer == null)
			return;
		RemovePlayerTracking(despawnedPlayer);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC spawnedNpc = event.getNpc();
		if (spawnedNpc == null)
			return;
		TrackNpc(spawnedNpc);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC despawnedNpc = event.getNpc();
		if (despawnedNpc == null)
			return;
		RemoveNpcTracking(despawnedNpc);
	}

	/**
	 * When an NPC changes forms, it frees existing tiles, and blocks new tiles
	 */
	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC changedNpc = event.getNpc();

		if (changedNpc == null)
			return;

		NPCComposition newComposition = changedNpc.getComposition();
		if (newComposition == null)
			return;

		NpcState npcState = trackedNpcMapping.get(changedNpc.getIndex());

		int oldSize = event.getOld().getSize();
		ModifyBlockingGrid(npcState.lastLocation,oldSize,npcState,false);

		int newSize = newComposition.getSize();
		ModifyBlockingGrid(npcState.trackedNPC.getWorldLocation(),newSize,npcState,true);

	}

	/**
	 * Effectively tracks the most recent movement of tracked players and NPCs
	 * Due to players running, single tick pathing can release a varying collection of tiles
	 * When an npc is "stuck" and cannot reach its target, it will re-block all of its present tiles every game tick
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{

		//loop through tracked players to detect movement
		for (PlayerState playerState : trackedPlayerMapping.values())
		{

			if (playerState.trackedPlayer == null)
				continue;

			if (!playerState.trackedPlayer.getWorldLocation().equals(playerState.lastLocation))
			{
				//player movement occurred
				WorldPoint lastLocation = playerState.lastLocation;
				WorldPoint currentLocation = playerState.trackedPlayer.getWorldLocation();

				int deltaX = currentLocation.getX()-lastLocation.getX();
				int absDeltaX = Math.abs(deltaX);

				int deltaY = currentLocation.getY()-lastLocation.getY();
				int absDeltaY = Math.abs(deltaY);

				if (absDeltaX == 2 || absDeltaY == 2)
				{
					//direction delta is +1 or -1, the sign determines direction of change E/W(+1X/-1X) N/S(+1Y/-1Y)
					int directionalDeltaX = (int) Math.signum(deltaX);
					int directionalDeltaY = (int) Math.signum(deltaY);

					//movement was a run, 2 tiles are released
					if (absDeltaX == absDeltaY || (absDeltaX == 0 || absDeltaY == 0))
					{
						//movement was a diagonal or straight, as adding 0 to an axis wont do anything these are functionally the same result
						ModifyBlockingGeneric(playerState, new WorldPoint(lastLocation.getX()+directionalDeltaX,lastLocation.getY()+directionalDeltaY,lastLocation.getPlane()),false);
					}
					else
					{
						//movement was an L
						CollisionData[] cachedData = client.getTopLevelWorldView().getCollisionMaps();
						if (cachedData != null)
						{
							CollisionData collisionData = cachedData[lastLocation.getPlane()];
							if (collisionData != null)
							{
								//larger absolute delta takes precedence when blocking isn't considered
								if (absDeltaX > absDeltaY)
								{
									/**
									 * the tile that is released between movement when blocking is not present adjacent to the players new tile
									 * [1][2*][-]
									 * [-][-][3]
									 */
									WorldPoint expectedReleaseTile = new WorldPoint(lastLocation.getX()+directionalDeltaX,lastLocation.getY(),lastLocation.getPlane());

									/**
									 * the tile that is released between movement when blocking is present adjacent to the players new tile
									 * [1][-][-]
									 * [-][2*][3]
									 */
									WorldPoint shiftedReleaseTile = new WorldPoint(currentLocation.getX()-directionalDeltaX,currentLocation.getY(),lastLocation.getPlane());

									/**
									 * determines if we cant reach final tile on a given axis via traditional pathing, requires shift
									 */
									boolean yBlocking = tileHasBlocking(collisionData,0,directionalDeltaY,currentLocation,0,0);
									boolean xBlocking = tileHasBlocking(collisionData,directionalDeltaX,0,currentLocation,0,-directionalDeltaY);

									/**
									 * if blocking is present we release the shifted tile, otherwise release the expected tile
									 * to verify these results you can walk the path the player ran
									 */
									ModifyBlockingGeneric(playerState, (yBlocking || xBlocking) ? shiftedReleaseTile : expectedReleaseTile,false);
								}
								else
								{
									/**
									 * the tile that is released between movement when blocking is not present adjacent to the players new tile
									 * [1][-]
									 * [2*][-]
									 * [-][3]
									 */
									WorldPoint expectedReleaseTile = new WorldPoint(lastLocation.getX(),lastLocation.getY()+directionalDeltaY,lastLocation.getPlane());

									/**
									 * the tile that is released between movement when blocking is present adjacent to the players new tile
									 * [1][-]
									 * [-][2*]
									 * [-][3]
									 */
									WorldPoint shiftedReleaseTile = new WorldPoint(currentLocation.getX(),currentLocation.getY()-directionalDeltaY,lastLocation.getPlane());

									/**
									 * determines if we cant reach final tile on a given axis via traditional pathing, requires shift
									 */
									boolean xBlocking = tileHasBlocking(collisionData,directionalDeltaX,0,currentLocation,0,0);
									boolean yBlocking = tileHasBlocking(collisionData,0,directionalDeltaY,currentLocation,-directionalDeltaX,0);

									/**
									 * if blocking is present we release the shifted tile, otherwise release the expected tile
									 * to verify these results you can walk the path the player ran
									 */
									ModifyBlockingGeneric(playerState, (xBlocking || yBlocking) ? shiftedReleaseTile : expectedReleaseTile,false);
								}


							}
						}

					}
				}
				else if (absDeltaX == 1 && absDeltaY == 1)
				{
					//movement was a single tile diagonal - this is the only instance of running 1 tile, as such requires separate collision tracking
					CollisionData[] cachedData = client.getTopLevelWorldView().getCollisionMaps();
					if (cachedData != null)
					{
						CollisionData collisionData = cachedData[lastLocation.getPlane()];
						if (collisionData != null)
						{
							//determines if the start, or ending tile are blocked on a given Axis
							boolean startBlockedByX = tileHasBlocking(collisionData, deltaX,0, lastLocation, deltaX, 0);
							boolean endBlockedByX = tileHasBlocking(collisionData, -deltaX,0, currentLocation, -deltaX, 0);
							boolean startBlockedByY = tileHasBlocking(collisionData, 0, deltaY, lastLocation, 0, deltaY);
							boolean endBlockedByY = tileHasBlocking(collisionData, 0,-deltaY, currentLocation, 0, -deltaY);

							if (endBlockedByX || startBlockedByY)
							{
								ModifyBlockingGeneric(playerState, new WorldPoint(lastLocation.getX() + deltaX, lastLocation.getY(), lastLocation.getPlane()), false);
							}
							else if (startBlockedByX || endBlockedByY)
							{
								ModifyBlockingGeneric(playerState, new WorldPoint(lastLocation.getX(), lastLocation.getY() + deltaY, lastLocation.getPlane()), false);
							}
							//if neither condition is met, the diagonal was not a run therefore regular release/block occurs later on.
						}
					}
				}

				//release tile of players previous location
				ModifyBlockingGeneric(playerState, playerState.lastLocation,false);
				//block tile of players new location
				ModifyBlockingGeneric(playerState, playerState.trackedPlayer.getWorldLocation(), true);

				//update last location for movement change detection
				playerState.lastLocation = playerState.trackedPlayer.getWorldLocation();
			}
		}

		//loop through tracked npcs to detect movement
		for (NpcState npcState : trackedNpcMapping.values())
		{
			NPC trackedNPC = npcState.trackedNPC;

			if (trackedNPC == null)
				continue;

			WorldPoint currentLocation = trackedNPC.getWorldLocation();
			WorldPoint lastLocation = npcState.lastLocation;

			boolean locationChanged = !currentLocation.equals(lastLocation);
			Actor currentTarget = trackedNPC.getInteracting();
			Actor lastTarget = npcState.lastTarget;

			if (locationChanged)
			{
				//npc has moved
				//release tile set of npcs previous location
				ModifyBlockingNpc(npcState,lastLocation,false);
				//block tile set of npcs new location
				ModifyBlockingNpc(npcState,currentLocation,true);

				npcState.lastLocation = npcState.trackedNPC.getWorldLocation();
				npcState.stuckTicks = 0;
			}
			else if (currentTarget != null && currentTarget == lastTarget)
			{
				//npc has not moved, additionally npc has the same target as last tick
				if (!isMeleeDistance(trackedNPC))
				{
					//target is not in melee distance
					String[] options = trackedNPC.getComposition().getActions();
					if (options != null && options.length > 2 && "Attack".equals(options[1]))
					{
						//attackable npc, presume stuck and re-block tiles. -- this should be adjusted for magic and ranged variants of npcs at some point
						npcState.stuckTicks++;
						ModifyBlockingNpc(npcState, currentLocation,true);
					}
				}
				else if (npcState.stuckTicks > 0)
				{
					//not in melee distance, assume npc has been freed stop coloring it
					npcState.stuckTicks = 0;
				}

			}

			if (currentTarget != lastTarget)
			{
				//target has changed, reset stuck ticks and set the new last target for upcoming tick
				npcState.lastTarget = currentTarget;
				npcState.stuckTicks = 0;
			}

		}

	}

	/**
	 * Check if a world point has blocking for a given direction bitmask
	 */
	public boolean tileHasBlocking(CollisionData collisionData, int blockingBitmask, WorldPoint baseLocation, int xOff, int yOff)
	{
		if (collisionData != null)
		{
			Point sceneTile = GetSceneTile(baseLocation, xOff, yOff);
			int blockingFlags = collisionData.getFlags()[sceneTile.getX()][sceneTile.getY()];
			return  ((blockingFlags & (blockingBitmask | CollisionDataFlag.BLOCK_MOVEMENT_FULL)) != 0);
		}
		return false;
	}

	/**
	 * Returns the above method, providing a bitmask generated by x/y directions
	 */
	public boolean tileHasBlocking(CollisionData collisionData, int xDirection, int yDirection, WorldPoint endLocation, int xOff, int yOff)
	{
		return tileHasBlocking(collisionData, getFlagsForDirections(xDirection,yDirection), endLocation, xOff, yOff);
	}

	/**
	 * Use tile-entry direction to determine flag bitmask
	 */
	int getFlagsForDirections(int xDirection, int yDirection)
	{
		int flagX = (xDirection == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_WEST : xDirection == -1 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST : 0);
		int flagY = (yDirection == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_SOUTH : yDirection == -1 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH : 0);
		return (flagX+flagY);
	}

	/**
	 * Convert a world point to the respective tile of the current scene
	 */
	public Point GetSceneTile(WorldPoint worldPoint,int xOff,int yOff)
	{
		return new Point(worldPoint.getX()-client.getTopLevelWorldView().getBaseX()+xOff,
				worldPoint.getY()-client.getTopLevelWorldView().getBaseY()+yOff);
	}

	/**
	 * Re-generate blocking if the list of npcs has changed, ideally we dont track all npcs at all times to prevent unnecessary processing
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!configChanged.getGroup().equals(BlockTrackerConfig.GROUP))
		{
			return;
		}

		CacheConfigs();

		if (configChanged.getKey().equals("trackedNpcs"))
		{
			generateInitialBlocking();
		}

	}

	/**
	 * Cache configs to prevent config calls in render
	 */
	public void CacheConfigs()
	{
		configBlockedTileColor = config.blockedTileColor();
		configStuckTileColor = config.stuckTileColor();

		configBorderWidth = config.borderWidth();
		configStuckTicks = config.stuckTicks();
		configTrackedNpcs = config.trackedNpcs();

		configShowPlayerBlocking = config.showPlayerBlocking();
		configShowLocalPlayerBlocking = config.showLocalPlayerBlocking();
		configShowTrackingConditionally = config.showTrackingConditionally();
	}


	/**
	 * Determines if an npc is within melee distance to its target
	 * This is used to assume an npc MIGHT be stuck, this can be incorrect for npcs that have mage/ranged attacks and in future needs to be reworked.
	 */
	public static boolean isMeleeDistance(NPC npc)
	{

		WorldPoint npcPoint = npc.getWorldLocation();
		WorldPoint targetPoint = npc.getInteracting().getWorldLocation();
		int size = npc.getComposition().getSize();

		int npcMaxX = npcPoint.getX() + size - 1;
		int npcMaxY = npcPoint.getY() + size - 1;

		boolean isAdjacentX = (targetPoint.getX() == npcPoint.getX() - 1 || targetPoint.getX() == npcMaxX + 1) && targetPoint.getY() >= npcPoint.getY() && targetPoint.getY() <= npcMaxY;
		boolean isAdjacentY = (targetPoint.getY() == npcPoint.getY() - 1 || targetPoint.getY() == npcMaxY + 1) && targetPoint.getX() >= npcPoint.getX() && targetPoint.getX() <= npcMaxX;

		//exactly 1 tile away from the npc and not diagonal
		return (isAdjacentY || isAdjacentX);
	}

	/**
	 * Modifies blocking state of provided World Point
	 */
	public void ModifyBlockingGeneric(EntityState entityState, WorldPoint worldPoint, boolean blocked)
	{
		if (blocked)
		{
			tileMapping.put(worldPoint,entityState);
		}
		else
		{
			tileMapping.remove(worldPoint);
		}
	}

	/**
	 * Validates an NPC and sets the blocking state of all related tiles the npc exists on based on its size/origin
	 */
	public void ModifyBlockingNpc(NpcState npcState, WorldPoint location, boolean blocked)
	{

		if (npcState == null)
			return;

		NPC npc = npcState.trackedNPC;

		if (npc == null)
		{
			return;
		}


		if (npc.getComposition() == null)
		{
			tileMapping.remove(npc.getWorldLocation());
			return;
		}

		int npcSize = npc.getComposition().getSize();

		ModifyBlockingGrid(location,npcSize,npcState,blocked);
	}

	/**
	 * Modifies blocking state of a grid extending from the provided base WorldPoint
	 */
	public void ModifyBlockingGrid(WorldPoint location, int npcSize, EntityState entityState, boolean blocked)
	{
		for (int x=0; x<npcSize; x++)
		{
			for (int y=0; y<npcSize; y++)
			{
				WorldPoint blockingTile = new WorldPoint(location.getX() + x, location.getY() + y, location.getPlane());
				ModifyBlockingGeneric(entityState, blockingTile,blocked);
			}
		}
	}

	@Provides
	BlockTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlockTrackerConfig.class);
	}
}
