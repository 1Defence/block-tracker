package com.blocktracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Block Tracker"
)
public class BlockTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BlockTrackerConfig config;

	@Override
	protected void startUp() throws Exception
	{

	}

	@Override
	protected void shutDown() throws Exception
	{

	}

	@Provides
	BlockTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlockTrackerConfig.class);
	}
}
