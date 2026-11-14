package raccoonman.reterraforged.world.worldgen.feature.placement.poisson;

import java.util.Random;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import raccoonman.reterraforged.world.worldgen.noise.NoiseUtil;
import raccoonman.reterraforged.world.worldgen.util.PosUtil;

/**
 * Seam-invariant 2D Poisson-disc point sampler used for natural, non-grid tree and feature placement.
 */
public class FastPoisson {
    public static final ThreadLocal<FastPoisson> LOCAL_POISSON = ThreadLocal.withInitial(FastPoisson::new);

    private static final int STATE_UNDECIDED = 0;
    private static final int STATE_ACCEPTED = 1;
    private static final int STATE_REJECTED = 2;

    private final LongList points = new LongArrayList();
    private final LongList cellKeys = new LongArrayList();
    private final LongList priorities = new LongArrayList();
    private float[] radiiSq = new float[256];
    private int[] states = new int[256];

    /**
     * Visits all accepted Poisson-disc points falling within chunk {@code (chunkX, chunkZ)}.
     */
    public <Ctx> void visit(int seed, int chunkX, int chunkZ, Random random, FastPoissonContext context, Ctx ctx, Visitor<Ctx> visitor) {
        this.points.clear();
        this.cellKeys.clear();
        this.priorities.clear();

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int maxRadius = context.radius() * 2 + 2;
        this.collectCells(seed, startX, startZ, maxRadius, context);

        int count = this.points.size();
        if (count == 0) {
            return;
        }
        if (this.states.length < count) {
            this.states = new int[count];
        }
        for (int i = 0; i < count; i++) {
            this.states[i] = STATE_UNDECIDED;
        }

        // Resolve maximal independent set deterministically across chunks via strict cell-keyed priorities
        // so chunk borders introduce neither overlaps nor seam-clipping artifacts.
        boolean changed = true;
        for (int pass = 0; pass < count && changed; pass++) {
            changed = false;
            for (int i = 0; i < count; i++) {
                if (this.states[i] == STATE_UNDECIDED && this.isLocalWinner(i, count)) {
                    this.acceptAndCullNeighbours(i, count);
                    changed = true;
                }
            }
        }

        for (int i = 0; i < count; i++) {
            if (this.states[i] == STATE_ACCEPTED) {
                long point = this.points.getLong(i);
                int px = PosUtil.unpackLeft(point);
                int pz = PosUtil.unpackRight(point);
                if (px >= startX && px < startX + 16 && pz >= startZ && pz < startZ + 16) {
                    visitor.visit(px, pz, ctx);
                }
            }
        }
    }

    public static <Ctx> void visit(int seed, int chunkX, int chunkZ, Random random, FastPoissonContext context, LongIterSet region, LongList chunk, Ctx ctx, Visitor<Ctx> visitor) {
        LOCAL_POISSON.get().visit(seed, chunkX, chunkZ, random, context, ctx, visitor);
    }

    private void collectCells(int seed, int startX, int startZ, int padBlocks, FastPoissonContext context) {
        int minCellX = NoiseUtil.floor((startX - padBlocks) * context.frequency());
        int maxCellX = NoiseUtil.floor((startX + 15 + padBlocks) * context.frequency());
        int minCellZ = NoiseUtil.floor((startZ - padBlocks) * context.frequency());
        int maxCellZ = NoiseUtil.floor((startZ + 15 + padBlocks) * context.frequency());

        float effectiveJitter = Math.max(context.jitter(), 0.75F);
        float pad = (1.0F - effectiveJitter) * 0.5F;
        float scale = context.scale();
        int radiusSqBase = context.radiusSq();

        for (int cz = minCellZ; cz <= maxCellZ; cz++) {
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                float ox = uniformHash01(seed, cx, cz);
                float oz = uniformHash01(seed ^ 0x9E3779B9, cx, cz);
                int px = NoiseUtil.floor((cx + pad + ox * effectiveJitter) * scale);
                int pz = NoiseUtil.floor((cz + pad + oz * effectiveJitter) * scale);

                int index = this.points.size();
                if (this.radiiSq.length <= index) {
                    this.radiiSq = java.util.Arrays.copyOf(this.radiiSq, this.radiiSq.length * 2);
                }
                long cellKey = PosUtil.pack(cx, cz);
                this.points.add(PosUtil.pack(px, pz));
                this.cellKeys.add(cellKey);
                this.priorities.add(pointPriority(seed, cx, cz));
                this.radiiSq[index] = radiusSqBase * context.density().compute(px, pz, 0);
            }
        }
    }

    private boolean isLocalWinner(int index, int count) {
        long point = this.points.getLong(index);
        int px = PosUtil.unpackLeft(point);
        int pz = PosUtil.unpackRight(point);
        float r2 = this.radiiSq[index];
        long prio = this.priorities.getLong(index);
        long cellKey = this.cellKeys.getLong(index);

        for (int j = 0; j < count; j++) {
            if (j == index || this.states[j] != STATE_UNDECIDED) {
                continue;
            }
            long other = this.points.getLong(j);
            int qx = PosUtil.unpackLeft(other);
            int qz = PosUtil.unpackRight(other);
            if (dist2(px, pz, qx, qz) <= Math.max(r2, this.radiiSq[j])) {
                int cmp = Long.compare(this.priorities.getLong(j), prio);
                if (cmp > 0 || (cmp == 0 && Long.compare(this.cellKeys.getLong(j), cellKey) > 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void acceptAndCullNeighbours(int index, int count) {
        this.states[index] = STATE_ACCEPTED;
        long point = this.points.getLong(index);
        int px = PosUtil.unpackLeft(point);
        int pz = PosUtil.unpackRight(point);
        float r2 = this.radiiSq[index];

        for (int j = 0; j < count; j++) {
            if (this.states[j] != STATE_UNDECIDED) {
                continue;
            }
            long other = this.points.getLong(j);
            int qx = PosUtil.unpackLeft(other);
            int qz = PosUtil.unpackRight(other);
            if (dist2(px, pz, qx, qz) <= r2) {
                this.states[j] = STATE_REJECTED;
            }
        }
    }

    private static float uniformHash01(int seed, int x, int z) {
        int h = NoiseUtil.hash2D(seed, x, z);
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static long pointPriority(int seed, int x, int z) {
        long h = ((long) seed << 32) ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xBF58476D1CE4E5B9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static int dist2(int ax, int az, int bx, int bz) {
        int dx = ax - bx;
        int dz = az - bz;
        return dx * dx + dz * dz;
    }

    public interface Visitor<Ctx> {
        void visit(int x, int z, Ctx ctx);
    }
}
