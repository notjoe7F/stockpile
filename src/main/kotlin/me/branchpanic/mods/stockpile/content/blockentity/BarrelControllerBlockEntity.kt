package me.branchpanic.mods.stockpile.content.blockentity

import me.branchpanic.mods.stockpile.content.block.BarrelControllerBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.util.math.BlockPos
import java.util.function.Supplier

class BarrelControllerBlockEntity(
    public var childPositions: Set<BlockPos> = emptySet()
) : BlockEntity(TYPE) {
    companion object {
        val TYPE: BlockEntityType<BarrelControllerBlockEntity> =
            BlockEntityType.Builder.create(Supplier { BarrelControllerBlockEntity() }, BarrelControllerBlock)
                .build(null)
    }

    val childEntities
        get() = childPositions.mapNotNull { world?.getBlockEntity(it) as? ItemBarrelBlockEntity }

    fun addChild(pos: BlockPos) {
        println("$childPositions")
        childPositions += pos
    }

    fun removeChild(pos: BlockPos) {
        println("$childPositions")
        childPositions -= pos
    }
}