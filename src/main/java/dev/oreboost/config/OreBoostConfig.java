package dev.oreboost.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Configuration immuable du mod. Une instance n'est jamais modifiée après sa création :
 * un rechargement produit une nouvelle instance, publiée d'un coup par {@link ConfigHolder}.
 */
public record OreBoostConfig(boolean enabled, boolean debug, Map<String, OreGroup> ores) {
	public static final double MIN_MULTIPLIER = 0.0;
	public static final double MAX_MULTIPLIER = 64.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Un groupe de minerai : un multiplicateur appliqué à une liste de placed features. */
	public record OreGroup(double multiplier, List<String> placedFeatures) {
		public OreGroup {
			placedFeatures = List.copyOf(placedFeatures);
		}
	}

	public OreBoostConfig {
		// LinkedHashMap pour conserver l'ordre du fichier dans /oreboost status.
		ores = Collections.unmodifiableMap(new LinkedHashMap<>(ores));
	}

	/** Valeurs par défaut : diamant et lapis x2, tout le reste en vanilla (1.0). */
	public static OreBoostConfig defaults() {
		Map<String, OreGroup> ores = new LinkedHashMap<>();
		ores.put("diamond", group(2.0, "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried"));
		ores.put("lapis", group(2.0, "ore_lapis", "ore_lapis_buried"));
		ores.put("coal", group(1.0, "ore_coal_upper", "ore_coal_lower"));
		ores.put("iron", group(1.0, "ore_iron_upper", "ore_iron_middle", "ore_iron_small"));
		ores.put("copper", group(1.0, "ore_copper", "ore_copper_large"));
		ores.put("gold", group(1.0, "ore_gold", "ore_gold_lower", "ore_gold_extra"));
		ores.put("redstone", group(1.0, "ore_redstone", "ore_redstone_lower"));
		ores.put("emerald", group(1.0, "ore_emerald"));
		ores.put("nether_quartz", group(1.0, "ore_quartz_nether", "ore_quartz_deltas"));
		ores.put("nether_gold", group(1.0, "ore_gold_nether", "ore_gold_deltas"));
		ores.put("ancient_debris", group(1.0, "ore_debris_small", "ore_ancient_debris_large"));
		return new OreBoostConfig(true, false, ores);
	}

	private static OreGroup group(final double multiplier, final String... paths) {
		List<String> ids = new ArrayList<>(paths.length);
		for (String path : paths) {
			ids.add("minecraft:" + path);
		}
		return new OreGroup(multiplier, ids);
	}

	/**
	 * Charge la config depuis le disque.
	 * - fichier absent : il est créé avec les valeurs par défaut ;
	 * - JSON invalide : erreur loggée, valeurs par défaut en mémoire, le fichier n'est PAS écrasé.
	 */
	public static OreBoostConfig load(final Path file, final Logger logger) {
		if (Files.notExists(file)) {
			OreBoostConfig defaults = defaults();
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(defaults.toJson()) + "\n", StandardCharsets.UTF_8);
				logger.info("Config par défaut créée : {}", file);
			} catch (IOException e) {
				logger.error("Impossible d'écrire la config par défaut dans {}", file, e);
			}
			return defaults;
		}

		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			return parse(JsonParser.parseString(text), logger);
		} catch (IOException | JsonParseException | IllegalStateException | ClassCastException | NumberFormatException e) {
			logger.error(
				"Config {} invalide ({}). Les valeurs par défaut sont utilisées ; le fichier n'est pas modifié.",
				file,
				e.getMessage()
			);
			return defaults();
		}
	}

	private static OreBoostConfig parse(final JsonElement rootElement, final Logger logger) {
		if (!rootElement.isJsonObject()) {
			throw new JsonParseException("la racine doit être un objet JSON");
		}
		JsonObject root = rootElement.getAsJsonObject();
		boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
		boolean debug = root.has("debug") && root.get("debug").getAsBoolean();

		Map<String, OreGroup> ores = new LinkedHashMap<>();
		if (root.has("ores")) {
			if (!root.get("ores").isJsonObject()) {
				throw new JsonParseException("\"ores\" doit être un objet");
			}
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("ores").entrySet()) {
				String name = entry.getKey();
				if (!entry.getValue().isJsonObject()) {
					throw new JsonParseException("le groupe \"" + name + "\" doit être un objet");
				}
				JsonObject group = entry.getValue().getAsJsonObject();

				double multiplier = group.has("multiplier") ? group.get("multiplier").getAsDouble() : 1.0;
				if (Double.isNaN(multiplier)) {
					throw new JsonParseException("multiplier du groupe \"" + name + "\" n'est pas un nombre");
				}
				double clamped = Math.clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
				if (clamped != multiplier) {
					logger.warn(
						"Groupe \"{}\" : multiplier {} hors bornes [{}, {}], ramené à {}.",
						name, multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER, clamped
					);
				}

				List<String> ids = new ArrayList<>();
				if (group.has("placed_features")) {
					JsonArray array = group.getAsJsonArray("placed_features");
					for (JsonElement id : array) {
						ids.add(id.getAsString());
					}
				}
				ores.put(name, new OreGroup(clamped, ids));
			}
		}
		return new OreBoostConfig(enabled, debug, ores);
	}

	private JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("enabled", this.enabled);
		root.addProperty("debug", this.debug);
		JsonObject oresJson = new JsonObject();
		this.ores.forEach((name, group) -> {
			JsonObject groupJson = new JsonObject();
			groupJson.addProperty("multiplier", group.multiplier());
			JsonArray ids = new JsonArray();
			group.placedFeatures().forEach(ids::add);
			groupJson.add("placed_features", ids);
			oresJson.add(name, groupJson);
		});
		root.add("ores", oresJson);
		return root;
	}
}
