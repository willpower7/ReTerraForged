package raccoonman.reterraforged.world.worldgen.surface.rule;

import java.util.Comparator;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceRules.Context;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;
import raccoonman.reterraforged.world.worldgen.util.PosUtil;

/**
 * Surface rule that selects a child rule based on the highest threshold exceeded by a 2D noise field.
 */
record NoiseRule(Holder<Noise> noise, List<Pair<Float, SurfaceRules.RuleSource>> rules) implements SurfaceRules.RuleSource {
	public static final Codec<NoiseRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Noise.CODEC.fieldOf("noise").forGetter(NoiseRule::noise),
		entryCodec().listOf().fieldOf("rules").forGetter(NoiseRule::rules)
	).apply(instance, NoiseRule::new));

	@Override
	public Rule apply(Context ctx) {
		Noise seeded = (Object) ctx.randomState instanceof RTFRandomState rtfRandomState
			? rtfRandomState.seed(this.noise.value())
			: this.noise.value();
		List<Pair<Float, SurfaceRules.SurfaceRule>> sortedRules = this.rules.stream()
			.map(pair -> Pair.of(pair.getFirst(), pair.getSecond().apply(ctx)))
			.sorted(Comparator.<Pair<Float, SurfaceRules.SurfaceRule>, Float>comparing(Pair::getFirst).reversed())
			.toList();
		return new Rule(seeded, sortedRules);
	}

	@Override
	public KeyDispatchDataCodec<NoiseRule> codec() {
		return new KeyDispatchDataCodec<>(CODEC);
	}

	private static Codec<Pair<Float, SurfaceRules.RuleSource>> entryCodec() {
		return RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("threshold").forGetter(Pair::getFirst),
			SurfaceRules.RuleSource.CODEC.fieldOf("rule").forGetter(Pair::getSecond)
		).apply(instance, Pair::new));
	}

	private static class Rule implements SurfaceRules.SurfaceRule {
		private final Noise noise;
		private final List<Pair<Float, SurfaceRules.SurfaceRule>> rules;
		private long lastPos;
		private SurfaceRules.SurfaceRule rule;

		public Rule(Noise noise, List<Pair<Float, SurfaceRules.SurfaceRule>> rules) {
			this.noise = noise;
			this.rules = rules;
			this.lastPos = Long.MIN_VALUE;
		}

		@Override
		public BlockState tryApply(int x, int y, int z) {
			long pos = PosUtil.pack(x, z);
			if (this.lastPos != pos) {
				float value = this.noise.compute(x, z, 0);
				SurfaceRules.SurfaceRule newRule = null;
				for (Pair<Float, SurfaceRules.SurfaceRule> entry : this.rules) {
					if (value > entry.getFirst()) {
						newRule = entry.getSecond();
						break;
					}
				}
				this.lastPos = pos;
				this.rule = newRule;
			}
			return this.rule != null ? this.rule.tryApply(x, y, z) : null;
		}
	}
}
