package dev.oreboost;

import dev.oreboost.config.ConfigHolder;
import dev.oreboost.config.OreBoostConfig;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OreBoost implements ModInitializer {
	public static final String MOD_ID = "oreboost";
	public static final Logger LOGGER = LoggerFactory.getLogger("OreBoost");

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
	}

	@Override
	public void onInitialize() {
		// Premier chargement : crée le fichier par défaut si besoin. L'index reste vide tant que
		// le registre dynamique PLACED_FEATURE n'existe pas (il appartient au serveur).
		ConfigHolder.publish(OreBoostConfig.load(configFile(), LOGGER), null, LOGGER);

		// SERVER_STARTING précède la création des mondes, donc l'index est prêt avant le premier chunk.
		// On relit le fichier ici pour qu'en solo chaque ouverture de monde prenne la config à jour.
		ServerLifecycleEvents.SERVER_STARTING.register(OreBoost::reload);

		// Les instances de PlacedFeature appartiennent au serveur qui s'arrête : on vide l'index.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ConfigHolder.publish(ConfigHolder.state().config(), null, LOGGER));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> OreBoostCommand.register(dispatcher));
	}

	/** Relit le fichier, reconstruit l'index et le publie de façon atomique. */
	public static ConfigHolder.State reload(final MinecraftServer server) {
		OreBoostConfig config = OreBoostConfig.load(configFile(), LOGGER);
		ConfigHolder.State state = ConfigHolder.publish(config, server.registryAccess(), LOGGER);
		LOGGER.info(
			"Config chargée : enabled={}, {} placed feature(s) modifiée(s), {} ID(s) non résolu(s).",
			config.enabled(), state.index().size(), state.unresolved().size()
		);
		return state;
	}
}
