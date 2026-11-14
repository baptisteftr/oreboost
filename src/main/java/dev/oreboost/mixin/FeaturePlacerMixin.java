package dev.oreboost.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.oreboost.config.ConfigHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;

/**
 * En 26.3 la logique de placement n'est plus dans PlacedFeature (devenu un record) mais dans
 * FeaturePlacer#place(PlacedFeature, RandomSource, BlockPos, boolean), par où passent à la fois
 * place() et placeWithBiomeCheck(). C'est donc cette méthode qu'on enveloppe.
 *
 * Pas de garde anti-récursion nécessaire : Operation#call exécute le corps d'origine directement,
 * sans repasser par ce wrapper.
 */
@Mixin(FeaturePlacer.class)
public abstract class FeaturePlacerMixin {
	@WrapMethod(
		method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z"
	)
	private boolean oreboost$multiplyPlacement(
		final PlacedFeature placedFeature,
		final RandomSource random,
		final BlockPos origin,
		final boolean biomeCheck,
		final Operation<Boolean> original
	) {
		double multiplier = ConfigHolder.multiplierFor(placedFeature);
		if (multiplier == 1.0) {
			// Chemin vanilla strict : aucun tirage aléatoire supplémentaire, monde identique.
			return original.call(placedFeature, random, origin, biomeCheck);
		}

		// n = floor(m) passes complètes, + 1 passe avec probabilité (m - n).
		// Le tirage utilise uniquement le RandomSource du placement (déterminisme par seed),
		// et n'a lieu que si la partie fractionnaire est non nulle.
		int runs = (int) Math.floor(multiplier);
		double fraction = multiplier - runs;
		if (fraction > 0.0 && random.nextDouble() < fraction) {
			runs++;
		}

		// Chaque passe rejoue toute la chaîne de placement modifiers (count, rarity_filter,
		// in_square, height_range, biome) : les hauteurs restent celles de vanilla.
		boolean placedAny = false;
		for (int i = 0; i < runs; i++) {
			placedAny |= original.call(placedFeature, random, origin, biomeCheck);
		}
		return placedAny;
	}
}
