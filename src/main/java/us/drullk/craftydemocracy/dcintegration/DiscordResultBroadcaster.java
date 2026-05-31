package us.drullk.craftydemocracy.dcintegration;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import net.minecraft.network.chat.Component;
import us.drullk.craftydemocracy.CraftyDemocracyMod;

public class DiscordResultBroadcaster implements ResultBroadcaster {

	@Override
	public void broadcast(Component results) {
		DiscordIntegration discord = DiscordIntegration.INSTANCE;
		if (discord == null) {
			CraftyDemocracyMod.LOGGER.debug("Discord Integration not ready; skipping result broadcast");
			return;
		}

		// https://github.com/ErdbeerbaerLP/DiscordIntegration-Core/blob/6979775317d4c98fdde5273c28262508f4295da3/src/main/java/de/erdbeerbaerlp/dcintegration/common/DiscordIntegration.java#L855
		discord.sendMessage(results.getString());
	}

}
