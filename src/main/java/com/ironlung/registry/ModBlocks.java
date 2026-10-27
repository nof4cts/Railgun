package com.ironlung.registry;

import com.ironlung.IronLungMod;
import com.ironlung.block.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, IronLungMod.MODID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(5.0f, 1200.0f).sound(SoundType.METAL);
    }

    public static final RegistryObject<Block> HULL_PLATE = reg("hull_plate", () -> new Block(metal()));
    public static final RegistryObject<Block> HULL_RIVETED = reg("hull_riveted", () -> new Block(metal()));
    public static final RegistryObject<Block> HULL_SEAM = reg("hull_seam", () -> new Block(metal()));
    public static final RegistryObject<Block> BOLTED_PANEL = reg("bolted_panel", () -> new Block(metal()));
    public static final RegistryObject<Block> GRATE_FLOOR = reg("grate_floor", () -> new Block(metal()));
    public static final RegistryObject<Block> WARNING_PLATE = reg("warning_plate", () -> new FacingBlock(metal()));
    public static final RegistryObject<Block> PIPE = reg("pipe", () -> new PipeBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> CAGE_LAMP = reg("cage_lamp", () -> new CageLampBlock(metal().noOcclusion()
            .lightLevel(s -> s.getValue(CageLampBlock.LIT) ? 9 : 0)));

    public static final Map<ControlType, RegistryObject<Block>> CONTROLS = new EnumMap<>(ControlType.class);

    static {
        for (ControlType t : ControlType.values()) {
            final int glow = t.glow;
            String name = t.name().toLowerCase(java.util.Locale.ROOT);
            CONTROLS.put(t, reg(name, () -> new ControlBlock(t, metal()
                    .lightLevel(s -> s.getValue(ControlBlock.POWERED) ? glow : 0))));
        }
    }

    public static Block control(ControlType t) {
        return CONTROLS.get(t).get();
    }

    private static RegistryObject<Block> reg(String name, Supplier<Block> sup) {
        RegistryObject<Block> ro = BLOCKS.register(name, sup);
        ModItems.ITEMS.register(name, () -> new BlockItem(ro.get(), new Item.Properties()));
        return ro;
    }
}
