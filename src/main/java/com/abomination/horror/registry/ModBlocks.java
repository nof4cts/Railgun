package com.abomination.horror.registry;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.block.FleshGrowthBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AbominationMod.MODID);

    public static final RegistryObject<Block> FLESH_GROWTH = BLOCKS.register("flesh_growth", () -> new FleshGrowthBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(0.4F)
                    .sound(SoundType.SLIME_BLOCK)
                    .lightLevel(s -> 4)
                    .randomTicks()
                    .noOcclusion()
                    .noCollission()
                    .pushReaction(PushReaction.DESTROY)));

    private ModBlocks() {}
}
