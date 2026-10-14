package raccoonman.reterraforged.world.worldgen.cell.heightmap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.WorldGenFlags;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;

public class WorldLookup {
	private float waterLevel;
	private float beachLevel;
	@Nullable
	private TileCache cache;
	private Heightmap heightmap;
	private Levels levels;
	
	public WorldLookup(GeneratorContext context) {
		this.cache = context.cache;
		this.heightmap = context.generator.getHeightmap();
		this.waterLevel = context.levels.water;
		this.beachLevel = context.levels.water(5);
		this.levels = context.levels;
	}
	
	public Heightmap getHeightmap() {
		return this.heightmap;
	}

	public int getGenerationHeight(int chunkX, int chunkZ, NoiseGeneratorSettings generatorSettings, boolean load) {
		NoiseSettings noiseSettings = generatorSettings.noiseSettings();
		
		int minY = noiseSettings.minY();
		int genHeight = noiseSettings.height();
		int cellHeight = noiseSettings.getCellHeight();
		
		if(!WorldGenFlags.cullNoiseSections() || this.cache == null) {
			return genHeight;
		}
		
		@Nullable
		Tile tile = load ? this.cache.provideAtChunk(chunkX, chunkZ) : this.cache.provideAtChunkIfPresent(chunkX, chunkZ);
		if(tile != null) {
			Tile.Chunk chunk = tile.getChunkReader(chunkX, chunkZ);
			int generationHeight = Math.max(generatorSettings.seaLevel(), this.levels.scale(chunk.getHighestPoint()));
			return Math.min(genHeight, ((-minY + generationHeight) / cellHeight + 1) * cellHeight);
		} else {
			return genHeight;
		}
	}

	public boolean applyCell(Cell cell, int x, int z, boolean applyClimate) {
		return this.applyCell(cell, x, z, false, applyClimate);
	}

	public boolean applyCell(Cell cell, int x, int z, boolean load, boolean applyClimate) {
		if (load && this.computeAccurate(cell, x, z)) {
			return true;
		}
		if (this.computeCached(cell, x, z)) {
			return true;
		}
		return this.compute(cell, x, z, applyClimate);
	}

	private boolean computeAccurate(Cell cell, int x, int z) {
		int rx = this.cache.chunkToTile(x >> 4);
		int rz = this.cache.chunkToTile(z >> 4);
		Tile tile = this.cache.provide(rx, rz);
		Cell c = tile.lookup(x, z);
		if (c != null) {
			cell.copyFrom(c);
		}
		return cell.terrain != null;
	}

	private boolean computeCached(Cell cell, int x, int z) {
		int rx = this.cache.chunkToTile(x >> 4);
		int rz = this.cache.chunkToTile(z >> 4);
		Tile tile = this.cache.provideIfPresent(rx, rz);
		if (tile != null) {
			Cell c = tile.lookup(x, z);
			if (c != null) {
				cell.copyFrom(c);
			}
			return cell.terrain != null;
		}
		return false;
	}

	private boolean compute(Cell cell, int x, int z, boolean applyClimate) {
		this.heightmap.apply(cell, x, z, applyClimate);
		if (cell.terrain == TerrainType.COAST && cell.height > this.waterLevel && cell.height <= this.beachLevel) {
			cell.terrain = TerrainType.BEACH;
		}
		return false;
	}
}
