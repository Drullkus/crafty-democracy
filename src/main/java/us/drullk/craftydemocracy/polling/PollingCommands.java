package us.drullk.craftydemocracy.polling;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import us.drullk.craftydemocracy.StringUtil;

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

		// TODO print player their chosen

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

		// TODO print player their chosen

		return 0;
	}

	private int setPoll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String name = StringArgumentType.getString(context, "name");
		int choiceLimit = IntegerArgumentType.getInteger(context, "choice_limit");
		String choices = StringArgumentType.getString(context, "choices");

		this.pollManager.setPoll(context.getSource().getServer(), name, choiceLimit, StringUtil.splitWhitespace(choices));

		return 0;
	}

}
