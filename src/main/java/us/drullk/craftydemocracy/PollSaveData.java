package us.drullk.craftydemocracy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class PollSaveData {

	private static final String FILE_EXT = ".nbt";

	public static final LevelResource POLLS_DIR = new LevelResource("polls");

	private static @NotNull Path getPollsDir(MinecraftServer server) {
		return server.getWorldPath(POLLS_DIR);
	}

	public static void mkDirs(MinecraftServer server) {
		getPollsDir(server).toFile().mkdirs();
	}

	public static Path getPollMeta(MinecraftServer server) {
		return getPollsDir(server).resolve("polling.dat"); // Different filename extension
	}

	public static Map<UUID, String> loadPoll(MinecraftServer server, String pollName) {
		File pollFile = getPollsDir(server).resolve(pollName + FILE_EXT).toFile();

		// TODO serialize from nbt, PlayerDataStorage for reference

		return Collections.emptyMap();
	}

	public static void savePoll(MinecraftServer server, String pollName, Map<UUID, String> pollVotes) {
		File pollFile = getPollsDir(server).resolve(pollName + FILE_EXT).toFile();

		// TODO serialize to nbt, PlayerDataStorage for reference
	}

}
