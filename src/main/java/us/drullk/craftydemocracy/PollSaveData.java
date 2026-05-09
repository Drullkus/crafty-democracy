package us.drullk.craftydemocracy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class PollSaveData {

	private static final Codec<HashMap<UUID, String>> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).xmap(HashMap::new, Function.identity());

	private static final String FILE_EXT = ".nbt";
	private static final String SUFFIX_OLD = ".old";

	public static final LevelResource POLLS_DIR = new LevelResource("polls");

	public static void mkDirs(MinecraftServer server) {
		getPollsDir(server).toFile().mkdirs();
	}

	private static @NotNull Path getPollsDir(MinecraftServer server) {
		return server.getWorldPath(POLLS_DIR);
	}

	private static @NotNull Path getPollsFile(MinecraftServer server, String pollName) {
		return getPollsDir(server).resolve(pollName + FILE_EXT);
	}

	private static @NotNull Path getPollsFileOld(MinecraftServer server, String pollName) {
		return getPollsDir(server).resolve(pollName + SUFFIX_OLD + FILE_EXT);
	}

	public HashMap<UUID, String> loadPoll(MinecraftServer server, String pollName) {
		this.guardPollName(pollName);

		Path pollsFile = getPollsFile(server, pollName);
		Path pollsFileOld = getPollsFileOld(server, pollName);

		Optional<CompoundTag> pollNbt = this.loadNbt(pollsFile).or(() -> this.loadNbt(pollsFileOld));
		return pollNbt.map(nbt -> CODEC.parse(NbtOps.INSTANCE, nbt))
				.flatMap(DataResult::resultOrPartial)
				.orElseGet(HashMap::new);
	}

	private Optional<CompoundTag> loadNbt(Path pollPath) {
		File pollFile = pollPath.toFile();
		if (pollFile.exists() && pollFile.isFile()) {
			try {
				return Optional.of(NbtIo.readCompressed(pollFile.toPath(), NbtAccounter.unlimitedHeap()));
			} catch (Exception exception) {
				CraftyDemocracyMod.LOGGER.warn("Failed to load poll data in {}", pollFile.getAbsolutePath(), exception);
			}
		}

		return Optional.empty();
	}

	public void savePoll(MinecraftServer server, String pollName, HashMap<UUID, String> pollVotes) throws IOException {
		this.guardPollName(pollName);

		Optional<CompoundTag> tag = this.serializePoll(pollVotes);

		if (tag.isEmpty()) {
			throw new IOException("Could not serialize poll data for " + pollName);
		}

		this.saveNbt(server, pollName, tag.get());
	}

	private Optional<CompoundTag> serializePoll(HashMap<UUID, String> pollVotes) {
		DataResult<Tag> encodeResult = CODEC.encodeStart(NbtOps.INSTANCE, pollVotes);
		return encodeResult.resultOrPartial().flatMap(tag -> tag instanceof CompoundTag compoundTag ? Optional.of(compoundTag) : Optional.empty());
	}

	private void saveNbt(MinecraftServer server, String pollName, CompoundTag tag) throws IOException {
		Path tempFile = Files.createTempFile(getPollsDir(server), "_" + pollName, FILE_EXT);

		NbtIo.writeCompressed(tag, tempFile);

		Path pollFile = getPollsFile(server, pollName);
		Path pollFileOld = getPollsFileOld(server, pollName);

		Util.safeReplaceFile(pollFile, tempFile, pollFileOld);
	}

	private void guardPollName(String pollName) {
		if (!StrUtil.isAlphaNumeric(pollName)) {
			throw new IllegalArgumentException("Poll name must be alphanumeric");
		}
	}

	private Path getPollMeta(MinecraftServer server) {
		return getPollsDir(server).resolve("_polling.dat"); // Different filename extension to avoid collision
	}

}
