package us.drullk.craftydemocracy;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import tamaized.beanification.Autowired;
import tamaized.beanification.BeanContext;
import us.drullk.craftydemocracy.io.PollIO;
import us.drullk.craftydemocracy.polling.PollingCommands;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CraftyDemocracyMod.MODID)
public class CraftyDemocracyMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "crafty_democracy";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

	static {
		BeanContext.configure().loggingSettings().enableInjectInto();
		BeanContext.init(MODID);
	}

	@Autowired
	public PollingCommands pollingCommands;

	public CraftyDemocracyMod() {
		BeanContext.injectInto(this);

		NeoForge.EVENT_BUS.addListener(this.pollingCommands::registerCommands);
		NeoForge.EVENT_BUS.addListener(this::mkDirs);
	}

	private void mkDirs(ServerAboutToStartEvent event) {
		PollIO.mkDirs(event.getServer());
	}

}
