package me.branchpanic.mods.stockpile.content.block

import alexiil.mc.lib.attributes.AttributeList
import alexiil.mc.lib.attributes.AttributeProvider
import me.branchpanic.mods.stockpile.content.blockentity.ItemBarrelBlockEntity
import net.minecraft.block.BlockState
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World

val BlockPos.neighbors: Iterable<BlockPos>
    get() = Direction.values().map { dir -> offset(dir) }

object ItemBarrelBlock : BarrelBlock<ItemBarrelBlockEntity>({ ItemBarrelBlockEntity() }), AttributeProvider {
    override fun addAllAttributes(world: World?, pos: BlockPos?, state: BlockState?, attributes: AttributeList<*>?) {
        if (world == null || pos == null || state == null || attributes == null) {
            return
        }

        (world.getBlockEntity(pos) as? ItemBarrelBlockEntity)?.let { b -> attributes.offer(b.localStorage) }
    }

    override fun onBlockRemoved(
        state: BlockState?,
        world: World?,
        pos: BlockPos?,
        newState: BlockState?,
        unknown: Boolean
    ) {
        if (world != null && pos != null) {
            (world.getBlockEntity(pos) as? ItemBarrelBlockEntity)?.parent?.removeChild(pos)
        }

        super.onBlockRemoved(state, world, pos, newState, unknown)

        pos?.neighbors?.forEach {
            (world?.getBlockEntity(it) as? ItemBarrelBlockEntity)?.parentPos = null
            (world?.getBlockEntity(it) as? ItemBarrelBlockEntity)?.referrerPos = null
            (world?.getBlockEntity(it) as? ItemBarrelBlockEntity)?.updateAndPropagateParents()
        }
    }

    override fun onPlaced(world: World?, pos: BlockPos?, state: BlockState?, placer: LivingEntity?, stack: ItemStack?) {
        if (pos == null || world == null) {
            return
        }

        (world.getBlockEntity(pos) as? ItemBarrelBlockEntity)?.updateAndPropagateParents()
    }
}
