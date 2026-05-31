package us.drullk.craftydemocracy.dcintegration;

import net.minecraft.network.chat.Component;

public class NoOpResultBroadcaster implements ResultBroadcaster {

	@Override
	public void broadcast(Component results) {}

}
