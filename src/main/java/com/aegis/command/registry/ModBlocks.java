package com.aegis.command.registry;

import com.aegis.command.AegisCommand;
import com.aegis.command.block.LaptopBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AegisCommand.MODID);

    public static final RegistryObject<Block> LAPTOP = BLOCKS.register("laptop", () -> new LaptopBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f, 6f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 6)));

    private ModBlocks() {}
}
