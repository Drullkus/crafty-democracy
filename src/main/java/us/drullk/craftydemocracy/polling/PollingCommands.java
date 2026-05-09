package us.drullk.craftydemocracy.polling;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import us.drullk.craftydemocracy.StringUtil;
import us.drullk.craftydemocracy.io.PollMetaData;

import java.util.*;
import java.util.function.UnaryOperator;

public class PollingCommands {

	private final PollManager pollManager = new PollManager();

	private boolean requireGM(CommandSourceStack cs) {
		return cs.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	public void registerCommands(RegisterCommandsEvent event) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("poll").executes(this::showBallot);

		this.registerUserCommands(root, event.getDispatcher());
		this.registerAdminCommands(root);

		event.getDispatcher().register(root);
	}

	private void registerUserCommands(LiteralArgumentBuilder<CommandSourceStack> root, CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> vote = Commands.literal("vote")
				.then(Commands.literal("list").executes(this::showBallot))
				.then(Commands.literal("replace").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::setVotes)))
				.then(Commands.literal("add").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::addVotes)))
				.then(Commands.literal("remove").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::removeVotes)))
				;

		root.then(vote);
	}

	private void registerAdminCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("set").requires(this::requireGM).then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("choice_limit", IntegerArgumentType.integer(1)).then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::setPoll)))));
		root.then(Commands.literal("announce").requires(this::requireGM).executes(this::announceResults));
	}

	private int announceResults(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();

		Map<String, Long> results = this.pollManager.getResults(server);

		List<MutableComponent> ballot = new ArrayList<>();
		ballot.add(Component.literal("Results"));
		for (Map.Entry<String, Long> entry : results.entrySet()) {
			ballot.add(Component.literal("\n" + entry.getKey() + ": " + entry.getValue()));
		}

		Component resultsList = ballot.stream().reduce(Component.empty(), MutableComponent::append);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(resultsList);
		}

		return 0;
	}

	private int showBallot(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		ServerPlayer player = source.getPlayer();

		if (player == null || player instanceof FakePlayer) {
			return 1;
		}

		this.respondChoices(source.getServer(), player.getGameProfile().getId(), context);

		return 0;
	}

	private int setVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();

		String choices = StringArgumentType.getString(context, "choices");

		ServerPlayer player = source.getPlayer();

		if (player == null || player instanceof FakePlayer) {
			return 1;
		}

		this.pollManager.setVotes(server, player.getGameProfile().getId(), StringUtil.splitWhitespace(choices));

		this.respondChoices(server, player.getGameProfile().getId(), context);

		return 0;
	}

	private int addVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();

		String choices = StringArgumentType.getString(context, "choices");

		ServerPlayer player = source.getPlayer();

		if (player == null || player instanceof FakePlayer) {
			return 1;
		}

		this.pollManager.addVotes(server, player.getGameProfile().getId(), StringUtil.splitWhitespace(choices));

		this.respondChoices(server, player.getGameProfile().getId(), context);

		return 0;
	}

	private int removeVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();

		String choices = StringArgumentType.getString(context, "choices");

		ServerPlayer player = source.getPlayer();

		if (player == null || player instanceof FakePlayer) {
			return 1;
		}

		this.pollManager.removeVotes(server, player.getGameProfile().getId(), StringUtil.splitWhitespace(choices));

		this.respondChoices(server, player.getGameProfile().getId(), context);

		return 0;
	}

	private Component getVotingList(MinecraftServer server, UUID player) {
		PollMetaData pollMetaData = this.pollManager.getPollMetaData(server);
		List<String> choices = pollMetaData.stringChoices();
		Set<String> chosen = ImmutableSet.copyOf(this.pollManager.getPlayerVotes(server, player));

		List<MutableComponent> ballot = new ArrayList<>();

		ballot.add(Component.literal("\nChoices:\n"));

		int choicesLeft = pollMetaData.choiceLimit() - chosen.size();

		for (String choice : choices) {
			MutableComponent line = Component.empty();

			line.append(" • ");
			line.append(choice);

			if (chosen.contains(choice)) {
				UnaryOperator<Style> unvoteTrigger = s -> s
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to remove vote for " + choice)))
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/poll vote remove " + choice))
						;

				line.append(Component.translatable(" [%s]", Component.literal("Unvote").withStyle(ChatFormatting.DARK_GREEN).withStyle(unvoteTrigger)));
			} else if (choicesLeft > 0) {
				UnaryOperator<Style> voteTrigger = s -> s
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to add vote for " + choice)))
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/poll vote add " + choice))
						;

				line.append(Component.translatable(" [%s]", Component.literal("Vote").withStyle(ChatFormatting.GREEN).withStyle(voteTrigger)));
			}

			line.append("\n");
			ballot.add(line);
		}

		ballot.add(Component.literal("Choices left: " + choicesLeft));

		return ballot.stream().reduce(Component.empty(), MutableComponent::append);
	}

	private void respondChoices(MinecraftServer server, UUID player, CommandContext<CommandSourceStack> context) {
		Component votingList = this.getVotingList(server, player);
		context.getSource().sendSystemMessage(votingList);
	}

	private static final SimpleCommandExceptionType ERROR_TOO_FEW_CHOICES = new SimpleCommandExceptionType(Component.literal("There must be at least 2 choices"));
	private static final SimpleCommandExceptionType ERROR_CHOICE_LIMIT_TOO_LOW = new SimpleCommandExceptionType(Component.literal("Players must be permitted at least 1 choice"));
	private static final Dynamic2CommandExceptionType ERROR_CHOICE_LIMIT_TOO_HIGH = new Dynamic2CommandExceptionType((choiceLimit, choiceCount) -> Component.literal("Players' choice limit (%s) is greater than actual list of options (%s)".formatted(choiceLimit, choiceCount)));
	private int setPoll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String name = StringArgumentType.getString(context, "name");
		int choiceLimit = IntegerArgumentType.getInteger(context, "choice_limit");
		List<String> choices = StringUtil.splitWhitespace(StringArgumentType.getString(context, "choices"));

		if (choices.size() < 2) {
			throw ERROR_TOO_FEW_CHOICES.create();
		} else if (choiceLimit < 1) {
			throw ERROR_CHOICE_LIMIT_TOO_LOW.create();
		} else if (choices.size() < choiceLimit) {
			throw ERROR_CHOICE_LIMIT_TOO_HIGH.create(choiceLimit, choices.size());
		}

		this.pollManager.setPoll(context.getSource().getServer(), name, choiceLimit, choices.stream().<Component>map(Component::literal).toList());

		Component response = Component.literal("Started poll %s with options %s, limited to %d choices".formatted(name, choices, choiceLimit));
		context.getSource().sendSystemMessage(response);

		return 0;
	}

}
