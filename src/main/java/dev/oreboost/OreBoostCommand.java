package dev.oreboost;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.oreboost.config.ConfigHolder;
import dev.oreboost.config.OreBoostConfig;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Commandes Brigadier purement serveur : un client vanilla les voit comme n'importe quelle commande. */
final class OreBoostCommand {
	private static final int MAX_COUNT_RADIUS = 32;

	private OreBoostCommand() {
	}

	static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("oreboost")
				// Niveau de permission 3 (admins) ou plus.
				.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.then(Commands.literal("reload").executes(OreBoostCommand::reload))
				.then(Commands.literal("status").executes(OreBoostCommand::status))
				.then(
					Commands.literal("count")
						.then(Commands.argument("rayon", IntegerArgumentType.integer(0, MAX_COUNT_RADIUS)).executes(OreBoostCommand::count))
				)
		);
	}

	private static int reload(final CommandContext<CommandSourceStack> context) {
		ConfigHolder.State state = OreBoost.reload(context.getSource().getServer());
		context.getSource().sendSuccess(
			() -> Component.literal(
				"OreBoost rechargé : " + state.index().size() + " placed feature(s) modifiée(s), "
					+ state.unresolved().size() + " ID(s) non résolu(s). S'applique aux prochains chunks générés."
			),
			true
		);
		return 1;
	}

	private static int status(final CommandContext<CommandSourceStack> context) {
		ConfigHolder.State state = ConfigHolder.state();
		OreBoostConfig config = state.config();
		StringBuilder text = new StringBuilder("OreBoost : enabled=").append(config.enabled()).append(", debug=").append(config.debug());
		config.ores().forEach((name, group) -> text.append("\n  ").append(name).append(" x").append(group.multiplier()));
		if (!state.unresolved().isEmpty()) {
			text.append("\n  IDs non résolus : ").append(String.join(", ", state.unresolved()));
		}
		context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	/** Outil de mesure (debug) : compte les blocs de minerai dans les chunks chargés autour de la source. */
	private static int count(final CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		if (!ConfigHolder.state().config().debug()) {
			source.sendFailure(Component.literal("Commande de debug : passez \"debug\": true dans oreboost.json puis /oreboost reload."));
			return 0;
		}

		int radius = IntegerArgumentType.getInteger(context, "rayon");
		ServerLevel level = source.getLevel();
		BlockPos center = BlockPos.containing(source.getPosition());
		int centerX = center.getX() >> 4;
		int centerZ = center.getZ() >> 4;

		// Par bloc : [nombre, Y min, Y max].
		Map<String, int[]> stats = new TreeMap<>();
		int chunks = 0;
		for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
			for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
				// getChunkNow ne charge ni ne génère rien : seuls les chunks déjà chargés sont comptés.
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				chunks++;
				countChunk(chunk, stats);
			}
		}

		StringBuilder text = new StringBuilder("OreBoost count : ").append(chunks).append(" chunk(s) chargé(s), rayon ").append(radius);
		stats.forEach((id, s) -> text.append("\n  ").append(id).append(" = ").append(s[0]).append(" (Y ").append(s[1]).append(" à ").append(s[2]).append(")"));
		String message = text.toString();
		OreBoost.LOGGER.info(message);
		source.sendSuccess(() -> Component.literal(message), false);
		return chunks;
	}

	private static void countChunk(final LevelChunk chunk, final Map<String, int[]> stats) {
		LevelChunkSection[] sections = chunk.getSections();
		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			// maybeHas regarde la palette : la plupart des sections sont écartées sans parcourir leurs 4096 blocs.
			if (section.hasOnlyAir() || !section.maybeHas(OreBoostCommand::isOre)) {
				continue;
			}
			int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (!isOre(state)) {
							continue;
						}
						int worldY = baseY + y;
						String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
						int[] s = stats.computeIfAbsent(id, k -> new int[] {0, Integer.MAX_VALUE, Integer.MIN_VALUE});
						s[0]++;
						s[1] = Math.min(s[1], worldY);
						s[2] = Math.max(s[2], worldY);
					}
				}
			}
		}
	}

	private static boolean isOre(final BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.endsWith("_ore") || path.equals("ancient_debris");
	}
}
