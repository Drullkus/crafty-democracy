package us.drullk.craftydemocracy.polling;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import tamaized.beanification.Autowired;
import us.drullk.craftydemocracy.StringUtil;
import us.drullk.craftydemocracy.io.PollMetaData;

import java.util.*;
import java.util.function.UnaryOperator;

@tamaized.beanification.Component
public class PollingCommands {

	private static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Component.literal("This command must be run by a player"));

	private final PollManager pollManager;

	public PollingCommands(@Autowired PollManager pollManager) {
		this.pollManager = pollManager;
	}

	private boolean requireGM(CommandSourceStack cs) {
		return cs.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	private ServerPlayer assertRealPlayer(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayer();
		if (player == null || player instanceof FakePlayer) {
			throw ERROR_NOT_PLAYER.create();
		}
		return player;
	}

	public void registerCommands(RegisterCommandsEvent event) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("poll").executes(this::showBallot);

		this.registerUserCommands(root);
		this.registerAdminCommands(root);

		event.getDispatcher().register(root);
	}

	private void registerUserCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
		LiteralArgumentBuilder<CommandSourceStack> vote = Commands.literal("vote")
				.then(Commands.literal("list").executes(this::showBallot))
				.then(Commands.literal("replace").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(ctx -> this.modifyVotes(ctx, this.pollManager::setVotes))))
				.then(Commands.literal("add").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(ctx -> this.modifyVotes(ctx, this.pollManager::addVotes))))
				.then(Commands.literal("remove").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(ctx -> this.modifyVotes(ctx, this.pollManager::removeVotes))))
				;

		root.then(vote);
	}

	private void registerAdminCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("set").requires(this::requireGM).then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("choice_limit", IntegerArgumentType.integer(1)).then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::setPoll)))));
		root.then(Commands.literal("get").requires(this::requireGM).executes(this::getResults));
		root.then(Commands.literal("announce").requires(this::requireGM).executes(this::announceResults));
		root.then(Commands.literal("end").requires(this::requireGM).executes(this::endPoll));
		root.then(Commands.literal("import").requires(this::requireGM).then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("choice_limit", IntegerArgumentType.integer(1)).executes(this::importPoll))));
	}

	private Component buildResults(MinecraftServer server) {
		Map<String, Long> results = this.pollManager.getResults(server);

		List<MutableComponent> ballot = new ArrayList<>();
		ballot.add(Component.literal("Results"));
		for (Map.Entry<String, Long> entry : results.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).toList()) {
			ballot.add(Component.literal("\n" + entry.getKey() + ": " + entry.getValue() + " votes"));
		}

		return ballot.stream().reduce(Component.empty(), MutableComponent::append);
	}

	private int getResults(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		source.sendSystemMessage(this.buildResults(source.getServer()));

		return 0;
	}

	private int announceResults(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		Component resultsList = this.buildResults(server);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(resultsList);
		}

		return 0;
	}

	private int endPoll(CommandContext<CommandSourceStack> context) {
		int ret = this.announceResults(context);

		this.pollManager.stopPoll(context.getSource().getServer());

		return ret;
	}

	private int showBallot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = this.assertRealPlayer(source);

		this.respondChoices(source.getServer(), player.getGameProfile().getId(), context);

		return 0;
	}

	@FunctionalInterface
	private interface VoteOp {
		void apply(MinecraftServer server, UUID player, List<String> choices) throws CommandSyntaxException;
	}

	private int modifyVotes(CommandContext<CommandSourceStack> context, VoteOp op) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
        UUID playerId = this.assertRealPlayer(source).getGameProfile().getId();
		List<String> choices = StringUtil.splitWhitespace(StringArgumentType.getString(context, "choices"));

		op.apply(server, playerId, choices);

		this.respondChoices(server, playerId, context);

		return 0;
	}

	private Component getVotingList(MinecraftServer server, UUID player) {
		Set<String> chosen = ImmutableSet.copyOf(this.pollManager.getPlayerVotes(server, player));

		List<MutableComponent> ballot = new ArrayList<>();
		ballot.add(Component.literal("\nChoices:\n"));

		PollMetaData pollMetaData = this.pollManager.getPollMetaData(server);
		int choicesLeft = pollMetaData.choiceLimit() - chosen.size();
		for (Component choice : pollMetaData.choices()) {
			MutableComponent line = Component.empty().append(Component.literal(" • ").withStyle(ChatFormatting.GRAY)).append(choice);

			String choiceString = choice.getString();
			if (chosen.contains(choiceString)) {
				line.append(this.makeInteractiveButton("Unvote", ChatFormatting.DARK_GREEN, "Click to remove vote for " + choiceString, "/poll vote remove \"" + choiceString + "\""));
			} else if (choicesLeft > 0) {
				line.append(this.makeInteractiveButton("Vote", ChatFormatting.GREEN, "Click to add vote for " + choiceString, "/poll vote add \"" + choiceString + "\""));
			}

			line.append("\n");
			ballot.add(line);
		}

		ballot.add(Component.literal("Choices left: " + choicesLeft));
		return ballot.stream().reduce(Component.empty(), MutableComponent::append);
	}

	private MutableComponent makeInteractiveButton(String label, ChatFormatting color, String hover, String command) {
		UnaryOperator<Style> trigger = s -> s
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover)))
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				;

		return Component.translatable(" [%s]", Component.literal(label).withStyle(color).withStyle(trigger));
	}

	private void respondChoices(MinecraftServer server, UUID player, CommandContext<CommandSourceStack> context) {
		Component votingList = this.getVotingList(server, player);
		context.getSource().sendSystemMessage(votingList);
	}

	private int setPoll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String name = StringArgumentType.getString(context, "name");
		int choiceLimit = IntegerArgumentType.getInteger(context, "choice_limit");
		List<String> choices = StringUtil.splitWhitespace(StringArgumentType.getString(context, "choices"));

		this.pollManager.setPoll(context.getSource().getServer(), name, choiceLimit, choices.stream().<Component>map(Component::literal).toList());

		Component response = Component.literal("Started poll %s with options %s, limited to %d choices".formatted(name, choices, choiceLimit));
		context.getSource().sendSystemMessage(response);

		return 0;
	}

	private int importPoll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String name = StringArgumentType.getString(context, "name");
		int choiceLimit = IntegerArgumentType.getInteger(context, "choice_limit");
		this.pollManager.importPoll(context.getSource().getServer(), name, choiceLimit);

		List<String> choices = this.pollManager.getPollMetaData(context.getSource().getServer()).stringChoices();
		Component response = Component.literal("Started poll %s with options %s, limited to %d choices".formatted(name, choices, choiceLimit));
		context.getSource().sendSystemMessage(response);

		return 0;
	}

}
