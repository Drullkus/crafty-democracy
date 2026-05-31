package us.drullk.craftydemocracy;

import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import tamaized.beanification.Autowired;
import tamaized.beanification.Bean;
import tamaized.beanification.BeanContext;
import us.drullk.craftydemocracy.dcintegration.DiscordResultBroadcaster;
import us.drullk.craftydemocracy.dcintegration.NoOpResultBroadcaster;
import us.drullk.craftydemocracy.dcintegration.ResultBroadcaster;
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
	private PollingCommands pollingCommands;

	@Autowired
	public PollIO pollingIO;

	public CraftyDemocracyMod() {
		BeanContext.injectInto(this);

		NeoForge.EVENT_BUS.addListener(this.pollingCommands::registerCommands);
		NeoForge.EVENT_BUS.addListener(this.pollingIO::mkDirs);
	}

	@Bean
	public static ResultBroadcaster resultBroadcaster() {
		return ModList.get().isLoaded("dcintegration")
				? new DiscordResultBroadcaster()
				: new NoOpResultBroadcaster();
	}

}
