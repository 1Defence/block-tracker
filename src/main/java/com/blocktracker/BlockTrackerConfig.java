package com.blocktracker;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.Color;

@ConfigGroup(BlockTrackerConfig.GROUP)
public interface BlockTrackerConfig extends Config
{
	String GROUP = "blocktracker";
	@Alpha
	@ConfigItem(
			position = 0,
			keyName = "tileColor",
			name = "Blocked Tile Color",
			description = "Configures the color of blocked tiles"
	)
	default Color blockedTileColor()
	{
		return new Color(0,0,0,100);
	}
	@Alpha
	@ConfigItem(
			position = 1,
			keyName = "stuckColor",
			name = "Stuck Tile Color",
			description = "Configures the color of stuck tiles, this occurs when an npc has aggression but cant reach the target it will reblock its tiles every tick"
	)
	default Color stuckTileColor()
	{
		return new Color(100,0,0,100);
	}

	@ConfigItem(
			position = 2,
			keyName = "borderWidth",
			name = "Border Width",
			description = "Width of the blocked tiles border"
	)
	default double borderWidth()
	{
		return 0.2d;
	}

	@ConfigItem(
			position = 3,
			keyName = "stuckTicks",
			name = "Stuck Ticks",
			description = "Once an npc is stuck for this many ticks it will change color, set to 0 to never have the tile change color. Recommended to be atleast 2"
	)
	default int stuckTicks()
	{
		return 2;
	}

	@ConfigItem(
			position = 4,
			keyName = "trackedNpcs",
			name = "Tracked Npcs",
			description = "List of npcs to track separated by commas, leave blank to track all. - Accuracy will be lost if non-tracked npcs pass the tracked ones."
	)
	default String trackedNpcs()
	{
		return "";
	}

	@ConfigItem(
			position = 5,
			keyName = "showPlayerBlocking",
			name = "Show Player Blocking",
			description = "Shows blocking of players, regardless of this setting players will be tracked for both blocking and unblocking of visual tiles."
	)
	default boolean showPlayerBlocking()
	{
		return true;
	}

	@ConfigItem(
			position = 6,
			keyName = "showLocalPlayerBlocking",
			name = "Show Local Player Blocking",
			description = "Additionally shows blocking of local player, Show Player Blocking must be enabled for this."
	)
	default boolean showLocalPlayerBlocking()
	{
		return false;
	}

	@ConfigItem(
			position = 7,
			keyName = "showTrackingConditionally",
			name = "Show Tracking Conditionally",
			description = "Only render tracking when tracked npcs are present, with this disabled player tracking will show at all times."
	)
	default boolean showTrackingConditionally()
	{
		return true;
	}
}
