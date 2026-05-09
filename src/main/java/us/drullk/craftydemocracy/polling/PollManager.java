package us.drullk.craftydemocracy.polling;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import us.drullk.craftydemocracy.io.PollMetaData;
import us.drullk.craftydemocracy.io.PollIO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PollManager {

	private static final SimpleCommandExceptionType ERROR_SET_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.poll.set.failed"));
	private static final SimpleCommandExceptionType ERROR_TOO_MANY_VOTES = new SimpleCommandExceptionType(Component.translatable("commands.poll.set.toomany"));

	private final PollIO pollIO = new PollIO();

	private PollMetaData pollMetaDataCache;
	private HashMap<UUID, List<String>> pollCache;

	public void setVotes(MinecraftServer server, UUID player, List<String> choices) throws CommandSyntaxException {
		PollMetaData pollMetaData = this.getPollMetaData(server);

		if (choices.size() > pollMetaData.choiceLimit()) {
			throw ERROR_TOO_MANY_VOTES.create();
		}

		HashMap<UUID, List<String>> poll = this.getPoll(server);
		poll.put(player, choices);
		try {
			this.savePoll(server, poll);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save poll", e);
		}
	}

	public void addVotes(MinecraftServer server, UUID player, List<String> list) throws CommandSyntaxException {
		List<String> choices = this.getPoll(server).computeIfAbsent(player, uuid -> new ArrayList<>());

		if (!(choices instanceof ArrayList)) choices = new ArrayList<>(choices);

		choices.addAll(list);

		this.setVotes(server, player, choices);
	}

	private PollMetaData getPollMetaData(MinecraftServer server) {
		if (this.pollMetaDataCache == null) {
			this.pollMetaDataCache = this.pollIO.loadPollMetaData(server);
		}
		return this.pollMetaDataCache;
	}

	public void setPoll(MinecraftServer server, String name, int choiceLimit, List<String> choices) throws CommandSyntaxException {
		this.setPollMetaData(server, new PollMetaData(name, choiceLimit, choices));
	}

	private void setPollMetaData(MinecraftServer server, PollMetaData pollMetaData) throws CommandSyntaxException {
		this.pollMetaDataCache = pollMetaData;

		try {
			this.pollIO.savePollMetaData(server, pollMetaData);
		} catch (IOException e) {
			throw ERROR_SET_FAILED.create();
		}
	}

	private HashMap<UUID, List<String>> getPoll(MinecraftServer server) {
		if (this.pollCache != null) {
			return this.pollCache;
		}

		String name = this.getPollMetaData(server).name();
		this.pollCache = this.pollIO.loadPoll(server, name);
		return this.pollCache;
	}
	
	private void savePoll(MinecraftServer server, HashMap<UUID, List<String>> poll) throws IOException {
		this.pollIO.savePoll(server, this.getPollMetaData(server).name(), poll);
	}
}
