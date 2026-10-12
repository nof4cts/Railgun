package com.firestar.orbital.server;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.config.OConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What Antipode leaves behind: the other world's flesh, alive in our ground. From the rim of the exchanged land a front
 * creeps outward with no end, column by column, turning soil, stone, trees and water to flesh, and things crawl out of it
 * near anyone who walks on it. The front is saved with the world, so it carries on after a restart.
 *
 * <p>With The Flesh That Hates installed it grows that mod's flesh blocks and spawns its creatures (with their own models
 * and animations); without it, the other world's alien soil and our own monsters.
 */
public final class Blight extends SavedData {
    private static final String NAME = "orbital_arsenal_blight";
    static final String FLESH = "the_flesh_that_hates";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Random R = new Random();

    /** Columns on the edge of the blight, packed as BlockPos.asLong(x, 0, z). */
    private final LongArrayList frontier = new LongArrayList();
    private final LongOpenHashSet onFrontier = new LongOpenHashSet();

    // ------------------------------------------------------------------------------------------------ persistence

    public static Blight get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Blight::load, Blight::new, NAME);
    }

    private static Blight load(CompoundTag tag) {
        Blight b = new Blight();
        for (long l : tag.getLongArray("frontier")) {
            b.add(l);
        }
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("frontier", new LongArrayTag(frontier.toLongArray()));
        return tag;
    }

    private void add(long col) {
        if (onFrontier.add(col)) {
            frontier.add(col);
        }
    }

    public int size() {
        return frontier.size();
    }

    public void clear() {
        frontier.clear();
        onFrontier.clear();
        setDirty();
    }

    // ------------------------------------------------------------------------------------------------ the palette

    static boolean flesh() {
        return net.minecraftforge.fml.ModList.get().isLoaded(FLESH);
    }

    private static BlockState block(String mod, String name, BlockState fallback) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(mod, name));
        return b == null || b == Blocks.AIR ? fallback : b.defaultBlockState();
    }

    static BlockState top(Random r) {
        if (flesh()) {
            int k = r.nextInt(100);
            return k < 70 ? block(FLESH, "flesh_grass", Blocks.CRIMSON_NYLIUM.defaultBlockState())
                    : k < 88 ? block(FLESH, "flesh_block", Blocks.CRIMSON_NYLIUM.defaultBlockState())
                    : k < 95 ? block(FLESH, "purulent", Blocks.CRIMSON_NYLIUM.defaultBlockState())
                    : block(FLESH, "tumor", Blocks.CRIMSON_NYLIUM.defaultBlockState());
        }
        int k = r.nextInt(100);
        return (k < 45 ? Blocks.CRIMSON_NYLIUM : k < 80 ? Blocks.WARPED_NYLIUM : Blocks.SCULK).defaultBlockState();
    }

    static BlockState under(Random r, boolean sand) {
        if (flesh()) {
            return sand ? block(FLESH, "flesh_sand", Blocks.NETHERRACK.defaultBlockState())
                    : r.nextInt(6) == 0 ? block(FLESH, "flesh_block_bone", Blocks.NETHERRACK.defaultBlockState())
                    : block(FLESH, "flesh_block", Blocks.NETHERRACK.defaultBlockState());
        }
        return (r.nextInt(30) == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.BLACKSTONE).defaultBlockState();
    }

    static BlockState deep() {
        return flesh() ? block(FLESH, "flesh_coublestone", Blocks.DEEPSLATE.defaultBlockState()) : Blocks.DEEPSLATE.defaultBlockState();
    }

    static BlockState growth(Random r) {
        if (flesh()) {
            int k = r.nextInt(100);
            return k < 55 ? block(FLESH, "flesh_small_grass", Blocks.AIR.defaultBlockState())
                    : k < 85 ? block(FLESH, "flesh_spike_grass", Blocks.AIR.defaultBlockState())
                    : k < 95 ? block(FLESH, "flesh_spikes", Blocks.AIR.defaultBlockState())
                    : block(FLESH, "flesh_boil", Blocks.AIR.defaultBlockState());
        }
        return (r.nextBoolean() ? Blocks.CRIMSON_ROOTS : Blocks.WARPED_ROOTS).defaultBlockState();
    }

    /** Is this block already the other world's? */
    static boolean infected(BlockState s) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        if (id == null) {
            return false;
        }
        if (id.getNamespace().equals(FLESH)) {
            return true;
        }
        return s.is(Blocks.CRIMSON_NYLIUM) || s.is(Blocks.WARPED_NYLIUM) || s.is(Blocks.SCULK) || s.is(Blocks.CRYING_OBSIDIAN);
    }

    // ------------------------------------------------------------------------------------------------ spreading

    /** Antipode has finished exchanging a disc: its rim becomes the front. */
    public static void seed(ServerLevel level, BlockPos centre, float radius) {
        if (!OConfig.SERVER.blight.get()) {
            return;
        }
        Blight b = get(level);
        int n = Math.max(24, (int) (radius * 6.3));
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            b.add(BlockPos.asLong(centre.getX() + (int) Math.round(Math.cos(a) * radius), 0, centre.getZ() + (int) Math.round(Math.sin(a) * radius)));
        }
        b.setDirty();
        OrbitalMod.LOGGER.info("[blight] seeded {} columns round {}", n, centre);
    }

    /** Infect one column: its ground, what grows on it, any water lying on it. Returns blocks changed. */
    static int infect(ServerLevel l, int x, int z, Random r) {
        int top = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int ground = l.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int used = 0;
        // trees and plants above the ground
        for (int y = top; y > ground; y--) {
            m.set(x, y, z);
            BlockState s = l.getBlockState(m);
            if (s.isAir()) {
                continue;
            }
            if (s.is(BlockTags.LOGS)) {
                l.setBlock(m, block(FLESH, "flesh_tree", Blocks.CRIMSON_STEM.defaultBlockState()), FLAGS);
            } else if (s.is(BlockTags.LEAVES)) {
                l.setBlock(m, block(FLESH, "flesh_foliage", Blocks.NETHER_WART_BLOCK.defaultBlockState()), FLAGS);
            } else if (!s.getFluidState().isEmpty()) {
                if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource() && flesh()) {
                    l.setBlock(m, block(FLESH, "pus", s), FLAGS);
                } else {
                    continue;
                }
            } else if (s.canBeReplaced()) {
                l.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
            } else {
                continue;
            }
            used++;
        }
        // the ground
        m.set(x, ground, z);
        BlockState g = l.getBlockState(m);
        if (g.isAir() || infected(g)) {
            return used;
        }
        boolean sand = g.is(BlockTags.SAND);
        boolean wet = top > ground && !l.getBlockState(m.set(x, ground + 1, z)).getFluidState().isEmpty();
        l.setBlock(m.set(x, ground, z), wet ? under(r, sand) : top(r), FLAGS);
        used++;
        for (int y = ground - 1; y > ground - 4; y--) {
            m.set(x, y, z);
            BlockState s = l.getBlockState(m);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.is(Blocks.BEDROCK)) {
                break;
            }
            l.setBlock(m, y > ground - 3 ? under(r, sand) : deep(), FLAGS);
            used++;
        }
        if (!wet && r.nextInt(5) == 0 && l.getBlockState(m.set(x, ground + 1, z)).isAir()) {
            l.setBlock(m, growth(r), FLAGS);
            used++;
        }
        return used;
    }

    public void tick(ServerLevel level, int budget) {
        if (frontier.isEmpty() || !OConfig.SERVER.blight.get()) {
            return;
        }
        int rate = OConfig.SERVER.blightRate.get();
        boolean changed = false;
        for (int i = 0; i < rate && budget > 0 && !frontier.isEmpty(); i++) {
            int idx = R.nextInt(frontier.size());
            long col = frontier.getLong(idx);
            int x = BlockPos.getX(col), z = BlockPos.getZ(col);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            // reach for an uninfected neighbour; if there are none left, this column is no longer on the edge
            int dx = R.nextInt(3) - 1, dz = R.nextInt(3) - 1;
            int nx = x + dx, nz = z + dz;
            if ((dx != 0 || dz != 0) && level.hasChunk(nx >> 4, nz >> 4)) {
                int ng = level.getHeight(Heightmap.Types.OCEAN_FLOOR, nx, nz) - 1;
                BlockState s = level.getBlockState(new BlockPos(nx, ng, nz));
                if (!infected(s) && !s.isAir()) {
                    budget -= infect(level, nx, nz, R);
                    add(BlockPos.asLong(nx, 0, nz));
                    changed = true;
                }
            }
            if (R.nextInt(6) == 0 && surrounded(level, x, z)) {
                // swap-remove
                long last = frontier.removeLong(frontier.size() - 1);
                if (idx < frontier.size()) {
                    frontier.set(idx, last);
                }
                onFrontier.remove(col);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private static boolean surrounded(ServerLevel l, int x, int z) {
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : n) {
            int nx = x + d[0], nz = z + d[1];
            if (!l.hasChunk(nx >> 4, nz >> 4)) {
                return false;
            }
            int g = l.getHeight(Heightmap.Types.OCEAN_FLOOR, nx, nz) - 1;
            BlockState s = l.getBlockState(new BlockPos(nx, g, nz));
            if (!infected(s) && !s.isAir()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ what crawls out

    private static final String[] COMMON = {"flesh_human", "flesh_dog", "plaquecreatureone", "flesh_servant", "flesh_piece"};
    private static final String[] UNCOMMON = {"plaquecreaturetwo", "flesh_hunter_two", "flesh_boomer", "plaquecontaminator", "flesh_suffer"};
    private static final String[] RARE = {"bruteplaquecreatureone", "plaquethreelegcreature", "flesh_howler", "flesh_hunter_three"};

    private static EntityType<?> pick(Random r) {
        if (flesh()) {
            String[] pool = r.nextInt(100) < 65 ? COMMON : r.nextInt(100) < 80 ? UNCOMMON : RARE;
            for (int tries = 0; tries < 4; tries++) {
                EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(FLESH, pool[r.nextInt(pool.length)]));
                if (t != null && t != EntityType.PIG) {
                    return t;
                }
            }
        }
        int k = r.nextInt(10);
        return k < 5 ? EntityType.ZOMBIE : k < 8 ? EntityType.HUSK : EntityType.SPIDER;
    }

    /** Now and then, near each player standing on or near blighted ground, something crawls up out of it. */
    public static void spawn(ServerLevel level) {
        if (!OConfig.SERVER.blight.get() || OConfig.SERVER.blightMobCap.get() <= 0) {
            return;
        }
        List<ServerPlayer> players = level.players();
        for (ServerPlayer p : players) {
            if (p.isSpectator() || R.nextInt(3) != 0) {
                continue;
            }
            int cap = OConfig.SERVER.blightMobCap.get();
            AABB around = p.getBoundingBox().inflate(56);
            int count = level.getEntitiesOfClass(Mob.class, around, e -> spawned(e)).size();
            if (count >= cap) {
                continue;
            }
            for (int tries = 0; tries < 6; tries++) {
                double a = R.nextDouble() * Math.PI * 2, d = 14 + R.nextDouble() * 34;
                int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (!infected(level.getBlockState(new BlockPos(x, y - 1, z)))) {
                    continue;
                }
                EntityType<?> type = pick(R);
                Entity e = type.create(level);
                if (!(e instanceof Mob mob)) {
                    continue;
                }
                mob.moveTo(x + 0.5, y, z + 0.5, R.nextFloat() * 360, 0);
                if (!level.noCollision(mob)) {
                    continue;
                }
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null, null);
                mob.getPersistentData().putBoolean("orbital_blight", true);
                level.addFreshEntity(mob);
                break;
            }
        }
    }

    private static boolean spawned(Entity e) {
        return e.getPersistentData().getBoolean("orbital_blight");
    }
}
