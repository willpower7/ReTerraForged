package raccoonman.reterraforged.mixin;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import raccoonman.reterraforged.data.worldgen.preset.settings.WorldSettings;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.WorldGenFlags;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.noise.NoiseUtil;

@Mixin(value = NoiseBasedChunkGenerator.class, priority = 9001 /* preserves Noisium compatibility */)
class MixinNoiseBasedChunkGenerator {
	
	@Shadow
	@Final
    private Holder<NoiseGeneratorSettings> settings;
	
	@Redirect(
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/NoiseSettings;height()I"
		),
		method = "fillFromNoise",
		require = 1
	)
    public int fillFromNoise(NoiseSettings settings, Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunkAccess2) {
		GeneratorContext generatorContext;
		ChunkPos chunkPos = chunkAccess2.getPos();
		if((Object) randomState instanceof RTFRandomState rtfRandomState && (generatorContext = rtfRandomState.generatorContext()) != null) {
			return generatorContext.lookup.getGenerationHeight(chunkPos.x, chunkPos.z, this.settings.value(), true);
		} else {
    		return settings.height();
    	}
    }

	@Redirect(
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/NoiseSettings;height()I"
		),
		require = 1,
		method = "iterateNoiseColumn"
	)
    private int iterateNoiseColumn(NoiseSettings settings, LevelHeightAccessor levelHeightAccessor, RandomState randomState, int blockX, int blockZ, @Nullable MutableObject<NoiseColumn> mutableObject, @Nullable Predicate<BlockState> predicate) {
		GeneratorContext generatorContext;
		if((Object) randomState instanceof RTFRandomState rtfRandomState && (generatorContext = rtfRandomState.generatorContext()) != null) {
			return generatorContext.lookup.getGenerationHeight(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ), this.settings.value(), !WorldGenFlags.fastLookups());
    	} else {
    		return settings.height();
    	}
    }
	
	@Inject(
		at = @At("TAIL"),
		method = "addDebugScreenInfo"
	)
    private void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos, CallbackInfo callback) {
		@Nullable
		GeneratorContext generatorContext;
		if((Object) randomState instanceof RTFRandomState rtfRandomState && (generatorContext = rtfRandomState.generatorContext()) != null) {
			Cell cell = new Cell();
			generatorContext.lookup.applyCell(cell, blockPos.getX(), blockPos.getZ(), true);

			WorldSettings worldSettings = generatorContext.preset.world();
			WorldSettings.ControlPoints controlPoints = worldSettings.controlPoints;
			
			list.add("");
			list.add("Terrain Type: " + cell.terrain.getName());
			list.add("Terrain Region: " + cell.terrainRegionEdge);
			list.add("Continent Edge: " + cell.continentEdge);
			list.add("Ground Variance: " + NoiseUtil.map(cell.continentEdge, 0.0F, 1.0F, controlPoints.coast, controlPoints.inland));
			list.add("Biome Type: " + cell.biome.name());
			list.add("Macro Biome: " + cell.macroBiomeId);
			list.add("Temperature: " + cell.regionTemperature);
			list.add("Moisture: " + cell.regionMoisture);
			list.add("");
    	}
    }
}
