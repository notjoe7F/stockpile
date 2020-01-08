package me.branchpanic.mods.stockpile.content.blockentity

import alexiil.mc.lib.attributes.item.impl.CombinedFixedItemInv
import me.branchpanic.mods.stockpile.content.block.BarrelControllerBlock
import me.branchpanic.mods.stockpile.impl.attribute.UnrestrictedInventoryFixedWrapper
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import java.util.function.Supplier

class BarrelControllerBlockEntity(
    private var childPositions: Set<BlockPos> = emptySet()
) : BlockEntity(TYPE), Inventory {
    companion object {
        val TYPE: BlockEntityType<BarrelControllerBlockEntity> =
            BlockEntityType.Builder.create(Supplier { BarrelControllerBlockEntity() }, BarrelControllerBlock)
                .build(null)
    }

    val childEntities
        get() = childPositions.mapNotNull { world?.getBlockEntity(it) as? ItemBarrelBlockEntity }

    val distributedStorage
        get() = CombinedFixedItemInv.create(childEntities.map { e -> e.localStorage })

    private val invWrapper
        get() = UnrestrictedInventoryFixedWrapper(distributedStorage)

    fun addChild(pos: BlockPos) {
        childPositions += pos
    }

    fun removeChild(pos: BlockPos) {
        childPositions -= pos
    }

    override fun getInvStack(slot: Int): ItemStack = invWrapper.getInvStack(slot)

    override fun clear() = invWrapper.clear()

    override fun setInvStack(slot: Int, stack: ItemStack?) = invWrapper.setInvStack(slot, stack)

    override fun removeInvStack(slot: Int): ItemStack = invWrapper.removeInvStack(slot)

    override fun canPlayerUseInv(player: PlayerEntity?): Boolean = invWrapper.canPlayerUseInv(player)

    override fun getInvSize(): Int = invWrapper.invSize

    override fun takeInvStack(slot: Int, amount: Int): ItemStack = invWrapper.takeInvStack(slot, amount)

    override fun isInvEmpty(): Boolean = invWrapper.isInvEmpty
}