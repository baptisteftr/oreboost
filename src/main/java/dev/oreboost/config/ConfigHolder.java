package dev.oreboost.config;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Point d'accès unique, thread-safe, à la config et à l'index résolu.
 * L'état est un objet immuable remplacé d'un seul coup : un thread de génération
 * voit soit l'ancien état complet, soit le nouveau, jamais un mélange.
 */
public final class ConfigHolder {
	/**
	 * @param index      instance de PlacedFeature -> multiplicateur. Construit une fois puis
	 *                   jamais modifié : la lecture concurrente d'une IdentityHashMap figée est sûre.
	 *                   Ne contient que les multiplicateurs != 1.0, et rien si enabled == false.
	 * @param unresolved IDs de la config introuvables dans le registre (pour /oreboost status).
	 */
	public record State(OreBoostConfig config, Map<PlacedFeature, Double> index, List<String> unresolved) {
	}

	private static final AtomicReference<State> STATE =
		new AtomicReference<>(new State(OreBoostConfig.defaults(), new IdentityHashMap<>(), List.of()));

	private ConfigHolder() {
	}

	public static State state() {
		return STATE.get();
	}

	/** Chemin chaud (threads de worldgen) : multiplicateur de cette instance, 1.0 si non ciblée. */
	public static double multiplierFor(final PlacedFeature feature) {
		Map<PlacedFeature, Double> index = STATE.get().index();
		if (index.isEmpty()) {
			return 1.0;
		}
		Double multiplier = index.get(feature);
		return multiplier == null ? 1.0 : multiplier;
	}

	/**
	 * Résout les IDs de la config contre le registre dynamique et publie le nouvel état.
	 * Sans registre (serveur pas encore démarré / arrêté), l'index publié est vide.
	 */
	public static State publish(final OreBoostConfig config, final @Nullable RegistryAccess registries, final Logger logger) {
		Map<PlacedFeature, Double> index = new IdentityHashMap<>();
		List<String> unresolved = new ArrayList<>();

		if (registries != null) {
			Registry<PlacedFeature> registry = registries.lookupOrThrow(Registries.PLACED_FEATURE);
			config.ores().forEach((groupName, group) -> {
				for (String rawId : group.placedFeatures()) {
					Identifier id = Identifier.tryParse(rawId);
					PlacedFeature feature = id == null ? null : registry.getValue(id);
					if (feature == null) {
						unresolved.add(rawId);
						logger.warn("Groupe \"{}\" : placed feature inconnue \"{}\", ignorée.", groupName, rawId);
						continue;
					}
					if (!config.enabled() || group.multiplier() == 1.0) {
						continue;
					}
					Double previous = index.put(feature, group.multiplier());
					if (previous != null && previous != group.multiplier()) {
						logger.warn(
							"\"{}\" est listée dans plusieurs groupes : le multiplicateur {} du groupe \"{}\" remplace {}.",
							rawId, group.multiplier(), groupName, previous
						);
					}
				}
			});
		}

		State state = new State(config, index, List.copyOf(unresolved));
		STATE.set(state);
		return state;
	}
}
