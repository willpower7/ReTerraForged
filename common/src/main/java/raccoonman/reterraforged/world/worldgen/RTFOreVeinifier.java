package raccoonman.reterraforged.world.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * Mirrors vanilla {@link OreVeinifier} while allowing iron ore veins to extend down into deeper worlds.
 */
public final class RTFOreVeinifier {
	private static final float VEININESS_THRESHOLD = 0.4F;
	private static final int EDGE_ROUNDOFF_BEGIN = 20;
	private static final double MAX_EDGE_ROUNDOFF = 0.2D;
	private static final float VEIN_SOLIDNESS = 0.7F;
	private static final float MIN_RICHNESS = 0.1F;
	private static final float MAX_RICHNESS = 0.3F;
	private static final float MAX_RICHNESS_THRESHOLD = 0.6F;
	private static final float CHANCE_OF_RAW_ORE_BLOCK = 0.02F;
	private static final float SKIP_ORE_IF_GAP_NOISE_IS_BELOW = -0.3F;

	private RTFOreVeinifier() {
	}

	public static NoiseChunk.BlockStateFiller create(
		DensityFunction veinToggle,
		DensityFunction veinRidged,
		DensityFunction veinGap,
		PositionalRandomFactory randomFactory,
		int ironMinY
	) {
		return context -> {
			double toggle = veinToggle.compute(context);
			int y = context.blockY();
			OreVeinifier.VeinType veinType = toggle > 0.0D ? OreVeinifier.VeinType.COPPER : OreVeinifier.VeinType.IRON;
			int minY = veinType == OreVeinifier.VeinType.IRON ? Math.min(veinType.minY, ironMinY) : veinType.minY;
			int distToTop = veinType.maxY - y;
			int distToBottom = y - minY;
			if (distToBottom < 0 || distToTop < 0) {
				return null;
			}
			int edgeDist = Math.min(distToTop, distToBottom);
			double roundoff = Mth.clampedMap(edgeDist, 0.0D, EDGE_ROUNDOFF_BEGIN, -MAX_EDGE_ROUNDOFF, 0.0D);
			double strength = Math.abs(toggle);
			if (strength + roundoff < VEININESS_THRESHOLD) {
				return null;
			}
			RandomSource random = randomFactory.at(context.blockX(), y, context.blockZ());
			if (random.nextFloat() > VEIN_SOLIDNESS) {
				return null;
			}
			if (veinRidged.compute(context) >= 0.0D) {
				return null;
			}
			double richness = Mth.clampedMap(strength, VEININESS_THRESHOLD, MAX_RICHNESS_THRESHOLD, MIN_RICHNESS, MAX_RICHNESS);
			if (random.nextFloat() < richness && veinGap.compute(context) > SKIP_ORE_IF_GAP_NOISE_IS_BELOW) {
				return random.nextFloat() < CHANCE_OF_RAW_ORE_BLOCK ? veinType.rawOreBlock : veinType.ore;
			}
			return veinType.filler;
		};
	}
}
