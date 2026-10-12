package com.firestar.orbital.registry;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.block.BlightPlant;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The other world's ground, as Antipode leaves it in ours. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, OrbitalMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OrbitalMod.MODID);

    public static final RegistryObject<Block> SOD = reg("antipode_sod", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_NYLIUM)
            .strength(0.6F).sound(SoundType.WART_BLOCK)));
    public static final RegistryObject<Block> FLESH = reg("antipode_flesh", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
            .strength(0.7F).sound(SoundType.WART_BLOCK)));
    public static final RegistryObject<Block> ROCK = reg("antipode_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PURPLE)
            .strength(1.6F, 6.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> VEIN = reg("antipode_vein", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA)
            .strength(0.7F).sound(SoundType.WART_BLOCK).lightLevel(s -> 9).emissiveRendering((s, l, p) -> true)));
    public static final RegistryObject<Block> MOSS = reg("antipode_moss", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_NYLIUM)
            .strength(0.6F).sound(SoundType.MOSS)));
    public static final RegistryObject<Block> TENDRILS = reg("antipode_tendrils", () -> new BlightPlant(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PINK).noCollission().instabreak().sound(SoundType.ROOTS).offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> BULB = reg("antipode_bulb", () -> new BlightPlant(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_MAGENTA).noCollission().instabreak().sound(SoundType.WART_BLOCK).lightLevel(s -> 7)
            .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> STALK = reg("antipode_stalk", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.STEM)));
    public static final RegistryObject<Block> CANOPY = reg("antipode_canopy", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED).strength(0.4F).sound(SoundType.WART_BLOCK).noOcclusion()));

    private static RegistryObject<Block> reg(String name, Supplier<Block> b) {
        RegistryObject<Block> r = BLOCKS.register(name, b);
        ITEMS.register(name, () -> new BlockItem(r.get(), new Item.Properties()));
        return r;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
