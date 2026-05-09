package us.drullk.craftydemocracy;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CraftyDemocracyMod.MODID)
public class CraftyDemocracyMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "crafty_democracy";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
}
