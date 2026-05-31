package us.drullk.craftydemocracy.polling;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import tamaized.beanification.Autowired;
import us.drullk.craftydemocracy.CollectionUtil;
import us.drullk.craftydemocracy.CraftyDemocracyMod;
import us.drullk.craftydemocracy.io.PollMetaData;
import us.drullk.craftydemocracy.io.PollIO;

import java.io.IOException;
import java.util.*;

@tamaized.beanification.Component
public class PollManager {

	private static final SimpleCommandExceptionType ERROR_IO_FAILED = new SimpleCommandExceptionType(Component.literal("Saving poll errored"));

	private static final DynamicCommandExceptionType ERROR_TOO_MANY_VOTES = new DynamicCommandExceptionType(arg -> Component.literal("Tried to vote for more choices than allowed (Limit: %s)".formatted(arg)));
	private static final DynamicCommandExceptionType ERROR_REPEAT_VOTES = new DynamicCommandExceptionType(arg -> Component.literal("Choices were repeated %s".formatted(arg)));
	private static final DynamicCommandExceptionType ERROR_NOT_CHOICES = new DynamicCommandExceptionType(arg -> Component.literal("Votes contained unlisted choices %s".formatted(arg)));
	private static final DynamicCommandExceptionType ERROR_MULTIPLE = new DynamicCommandExceptionType(arg -> Component.literal("Multiple errors: %s".formatted(arg)));

	private static final SimpleCommandExceptionType ERROR_TOO_FEW_CHOICES = new SimpleCommandExceptionType(Component.literal("There must be at least 2 choices"));
	private static final SimpleCommandExceptionType ERROR_CHOICE_LIMIT_TOO_LOW = new SimpleCommandExceptionType(Component.literal("Players must be permitted at least 1 choice"));
	private static final Dynamic2CommandExceptionType ERROR_CHOICE_LIMIT_TOO_HIGH = new Dynamic2CommandExceptionType((choiceLimit, choiceCount) -> Component.literal("Players' choice limit (%s) is greater than actual list of options (%s)".formatted(choiceLimit, choiceCount)));

	private final PollIO pollIO;

	private PollMetaData pollMetaDataCache;
	private HashMap<UUID, List<String>> pollCache;

	public PollManager(@Autowired PollIO pollIO) {
		this.pollIO = pollIO;
	}

	public void setVotes(MinecraftServer server, UUID player, List<String> choicesRaw) throws CommandSyntaxException {
		PollMetaData pollMetaData = this.getPollMetaData(server);

		List<String> choices = new ArrayList<>(new HashSet<>(choicesRaw));

		this.validate(server, choices, pollMetaData);

		HashMap<UUID, List<String>> poll = this.getPoll(server);
		poll.put(player, choices);
		try {
			this.savePoll(server, poll);
		} catch (IOException e) {
			CraftyDemocracyMod.LOGGER.error("Failed to save poll", e);
			throw ERROR_IO_FAILED.create();
		}

		CraftyDemocracyMod.LOGGER.info("{} voted for {}", player, choices);
	}

	private void validate(MinecraftServer server, List<String> choices, PollMetaData pollMetaData) throws CommandSyntaxException {
		List<CommandSyntaxException> errors = new ArrayList<>();

		List<String> exclusions = CollectionUtil.getExclusions(this.getPollMetaData(server).stringChoices(), choices);
		if (!exclusions.isEmpty()) {
			errors.add(ERROR_NOT_CHOICES.create(exclusions));
		}

		Map<String, Long> repeats = CollectionUtil.countRepeats(choices);
		if (!repeats.isEmpty()) {
			errors.add(ERROR_REPEAT_VOTES.create(repeats.keySet().stream().toList()));
		}

		if (choices.size() > pollMetaData.choiceLimit()) {
			errors.add(ERROR_TOO_MANY_VOTES.create(pollMetaData.choiceLimit()));
		}

		if (!errors.isEmpty()) {
			throw errors.size() == 1 ? errors.getFirst() : ERROR_MULTIPLE.create(errors.stream().map(CommandSyntaxException::getMessage).toList());
		}
	}

	public void addVotes(MinecraftServer server, UUID player, List<String> list) throws CommandSyntaxException {
		List<String> chosen = this.getPoll(server).get(player);

		ArrayList<String> copied = new ArrayList<>(chosen == null ? new ArrayList<>() : chosen);
		copied.addAll(list);

		this.setVotes(server, player, copied);
	}

	public void removeVotes(MinecraftServer server, UUID player, List<String> list) throws CommandSyntaxException {
		List<String> chosen = this.getPoll(server).get(player);

		ArrayList<String> copied = new ArrayList<>(chosen == null ? new ArrayList<>() : chosen);
		copied.removeAll(list);

		this.setVotes(server, player, copied);
	}

	public List<String> getPlayerVotes(MinecraftServer server, UUID player) {
		List<String> choices = this.getPoll(server).get(player);
		return choices == null ? Collections.emptyList() : choices;
	}

	public Map<String, Long> getResults(MinecraftServer server) {
		Map<String, Long> results = new HashMap<>();

		for (String choice : this.getPollMetaData(server).stringChoices()) {
			results.put(choice, 0L);
		}

		HashMap<UUID, List<String>> poll = this.getPoll(server);

		for (List<String> votes : poll.values()) {
			for (String vote : votes) {
				results.compute(vote, (k, v) -> v == null ? 1 : v + 1);
			}
		}

		return results;
	}

	public PollMetaData getPollMetaData(MinecraftServer server) {
		if (this.pollMetaDataCache == null) {
			this.pollMetaDataCache = this.pollIO.loadPollMetaData(server);
		}
		return this.pollMetaDataCache;
	}

	public void importPoll(MinecraftServer server, String name, int choiceLimit) throws CommandSyntaxException {
		this.setPollMetaData(server, this.pollIO.importCSV(server, name, choiceLimit));
	}

	public void setPoll(MinecraftServer server, String name, int choiceLimit, List<Component> choices) throws CommandSyntaxException {
		this.setPollMetaData(server, new PollMetaData(name, choiceLimit, choices));
	}

	private void setPollMetaData(MinecraftServer server, PollMetaData pollMetaData) throws CommandSyntaxException {
		if (pollMetaData.choices().size() < 2) {
			throw ERROR_TOO_FEW_CHOICES.create();
		} else if (pollMetaData.choiceLimit() < 1) {
			throw ERROR_CHOICE_LIMIT_TOO_LOW.create();
		} else if (pollMetaData.choices().size() < pollMetaData.choiceLimit()) {
			throw ERROR_CHOICE_LIMIT_TOO_HIGH.create(pollMetaData.choiceLimit(), pollMetaData.choices().size());
		}

		this.pollMetaDataCache = pollMetaData;

		try {
			this.pollIO.savePollMetaData(server, pollMetaData);
		} catch (IOException e) {
			CraftyDemocracyMod.LOGGER.error("Failed to save poll metadata", e);
			throw ERROR_IO_FAILED.create();
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

	public void stopPoll(MinecraftServer server) {
		this.pollMetaDataCache = null;
		this.pollCache = null;
		this.pollIO.stopActivePoll(server);
	}

}
