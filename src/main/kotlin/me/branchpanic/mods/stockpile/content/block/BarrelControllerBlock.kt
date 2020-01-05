package me.branchpanic.mods.stockpile.content.block

import me.branchpanic.mods.stockpile.content.blockentity.BarrelControllerBlockEntity
import me.branchpanic.mods.stockpile.content.blockentity.ItemBarrelBlockEntity
import net.fabricmc.fabric.api.block.FabricBlockSettings
import net.minecraft.block.Block
import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.world.World

object BarrelControllerBlock : Block(FabricBlockSettings.copy(Blocks.CHEST).build()),
    BlockEntityProvider {
    override fun createBlockEntity(view: BlockView?): BlockEntity? = BarrelControllerBlockEntity()

    override fun onBlockRemoved(
        state: BlockState?,
        world: World?,
        pos: BlockPos?,
        newState: BlockState?,
        moved: Boolean
    ) {
        if (world == null || world.isClient) return

        (world.getBlockEntity(pos) as? BarrelControllerBlockEntity)?.run {
            childEntities.forEach { e ->
                e.parentPos = null
                e.referrerPos = null
                markDirty()
            }
        }

        super.onBlockRemoved(state, world, pos, newState, moved)
    }

    override fun onPlaced(
        world: World?,
        pos: BlockPos?,
        state: BlockState?,
        placer: LivingEntity?,
        itemStack: ItemStack?
    ) {
        if (pos == null || world == null) return

        pos.neighbors.mapNotNull { world.getBlockEntity(it) as? ItemBarrelBlockEntity }
            .forEach { b -> b.updateAndPropagateParents() }
    }
}