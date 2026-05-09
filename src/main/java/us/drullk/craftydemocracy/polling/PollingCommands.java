package us.drullk.craftydemocracy.polling;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import us.drullk.craftydemocracy.StringUtil;
import us.drullk.craftydemocracy.io.PollMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PollingCommands {

	private final PollManager pollManager = new PollManager();

	private boolean requireGM(CommandSourceStack cs) {
		return cs.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	public void registerCommands(RegisterCommandsEvent event) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("poll");

		this.registerUserCommands(root);
		this.registerAdminCommands(root);

		event.getDispatcher().register(root);
	}

	private void registerUserCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("vote").then(Commands.literal("replace").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::setVotes))));
		root.then(Commands.literal("vote").then(Commands.literal("add").then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::addVotes))));

		// TODO view results command
		// TODO remove pick command
	}

	private void registerAdminCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("set").requires(this::requireGM).then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("choice_limit", IntegerArgumentType.integer(1)).then(Commands.argument("choices", StringArgumentType.greedyString()).executes(this::setPoll)))));

		// TODO announce results command
	}

	private int setVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();

		String choices = StringArgumentType.getString(context, "choices");

		ServerPlayer player = source.getPlayer();

		if (player == null || player instanceof FakePlayer) {
			return 1;
		}

		this.pollManager.setVotes(source.getServer(), player.getGameProfile().getId(), StringUtil.splitWhitespace(choices));

		this.respondChoices(source.getServer(), player.getGameProfile().getId(), context);

		return 0;
	}

	private int addVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();

		String choices = StringArgumentType.getString(context, "choices");

		ServerPlayer player = source.getPlayer();

		if (player == null || player instanceof FakePlayer) {
			return 1;
		}

		this.pollManager.addVotes(source.getServer(), player.getGameProfile().getId(), StringUtil.splitWhitespace(choices));

		this.respondChoices(source.getServer(), player.getGameProfile().getId(), context);

		return 0;
	}

	private Component getVotingList(MinecraftServer server, UUID player) {
		PollMetaData pollMetaData = this.pollManager.getPollMetaData(server);
		List<String> choices = pollMetaData.choices();
		Set<String> chosen = ImmutableSet.copyOf(this.pollManager.getPlayerVotes(server, player));

		List<MutableComponent> ballot = new ArrayList<>();

		for (String choice : choices) {
			MutableComponent line = Component.empty();

			line.append("- ");
			line.append(choice);

			if (chosen.contains(choice)) {
				line.append(" [Voted]");
			} else {
			}

			line.append("\n");
			ballot.add(line);
		}

		int choicesLeft = pollMetaData.choiceLimit() - chosen.size();
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

		this.pollManager.setPoll(context.getSource().getServer(), name, choiceLimit, choices);

		Component response = Component.literal("Started poll %s with options %s, limited to %d choices".formatted(name, choices, choiceLimit));
		context.getSource().sendSystemMessage(response);

		return 0;
	}

}
