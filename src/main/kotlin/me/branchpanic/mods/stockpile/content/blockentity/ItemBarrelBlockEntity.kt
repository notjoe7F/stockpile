package me.branchpanic.mods.stockpile.content.blockentity

import me.branchpanic.mods.stockpile.api.AbstractBarrelBlockEntity
import me.branchpanic.mods.stockpile.api.BarrelTransactionAmount
import me.branchpanic.mods.stockpile.api.upgrade.Upgrade
import me.branchpanic.mods.stockpile.api.upgrade.UpgradeContainer
import me.branchpanic.mods.stockpile.api.upgrade.UpgradeRegistry
import me.branchpanic.mods.stockpile.api.upgrade.barrel.ItemBarrelUpgrade
import me.branchpanic.mods.stockpile.content.block.ItemBarrelBlock
import me.branchpanic.mods.stockpile.content.block.neighbors
import me.branchpanic.mods.stockpile.content.upgrade.TrashUpgrade
import me.branchpanic.mods.stockpile.extension.giveTo
import me.branchpanic.mods.stockpile.extension.withCount
import me.branchpanic.mods.stockpile.impl.attribute.FixedMassItemInv
import me.branchpanic.mods.stockpile.impl.attribute.UnrestrictedInventoryFixedWrapper
import me.branchpanic.mods.stockpile.impl.storage.*
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable
import net.fabricmc.fabric.api.util.NbtType
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

class ItemBarrelBlockEntity(
    override var appliedUpgrades: List<Upgrade> = emptyList(),
    override var maxUpgrades: Int = DEFAULT_MAX_UPGRADES,
    public var parentPos: BlockPos? = null,
    public var referrerPos: BlockPos? = null
) : AbstractBarrelBlockEntity<ItemStack>(
    storage = MassItemStackStorage(ItemStackQuantifier.NONE, DEFAULT_CAPACITY_STACKS),
    clearWhenEmpty = true,
    doubleClickThresholdMs = 1000,
    type = TYPE
), BlockEntityClientSerializable, Inventory, UpgradeContainer {
    companion object {
        const val DEFAULT_CAPACITY_STACKS = 32
        const val DEFAULT_MAX_UPGRADES = 6

        const val STORED_ITEM_TAG = "StoredItem"
        const val AMOUNT_STORED_TAG = "AmountStored"
        const val CLEAR_WHEN_EMPTY_TAG = "ClearWhenEmpty"
        const val UPGRADE_TAG = "Upgrades"
        const val MAX_UPGRADES_TAG = "MaxUpgrades"
        const val PARENT_POS_TAG = "ParentPosition"
        const val REFERRER_POS_TAG = "ReferrerPosition"

        const val STORED_BLOCK_ENTITY_TAG = "StoredBlockEntity"

        val TYPE: BlockEntityType<ItemBarrelBlockEntity> =
            BlockEntityType.Builder.create(Supplier { ItemBarrelBlockEntity() }, ItemBarrelBlock).build(null)

        fun fromStack(stack: ItemStack): ItemBarrelBlockEntity {
            val barrel = ItemBarrelBlockEntity()
            barrel.fromClientTag(stack.getOrCreateSubTag(STORED_BLOCK_ENTITY_TAG))
            return barrel
        }
    }

    val localStorage = FixedMassItemInv(storage, false)
    private val invWrapper = UnrestrictedInventoryFixedWrapper(localStorage)

    init {
        localStorage.addListener({ markDirty() }, { })
    }

    override fun isUpgradeTypeAllowed(u: Upgrade): Boolean = u is ItemBarrelUpgrade

    override fun pushUpgrade(u: Upgrade) {
        appliedUpgrades += u
        markDirty()
    }

    override fun popUpgrade(): Upgrade {
        val result = appliedUpgrades.last()
        markDirty()
        appliedUpgrades = appliedUpgrades.dropLast(1)
        return result
    }

    override fun markDirty() {
        if (clearWhenEmpty && storage.isEmpty) {
            storage.contents = ItemStackQuantifier.NONE
        }

        handleUpgradeChanges()
        super.markDirty()

        world?.apply {
            updateListeners(pos, getBlockState(pos), getBlockState(pos), 3)
        }
    }

    private fun handleUpgradeChanges() {
        localStorage.voidExtraItems = appliedUpgrades.filterIsInstance<TrashUpgrade>().any()

        (storage as MassItemStackStorage).maxStacks = appliedUpgrades.filterIsInstance<ItemBarrelUpgrade>()
            .fold(DEFAULT_CAPACITY_STACKS) { acc, upgrade -> upgrade.upgradeMaxStacks(acc) }
    }

    override fun giveToPlayer(player: PlayerEntity, amount: BarrelTransactionAmount) {
        println("parent $parentPos, referred by $referrerPos")

        val removedItems = when (amount) {
            BarrelTransactionAmount.ONE -> storage.contents.withAmount(1)
            BarrelTransactionAmount.MANY -> storage.contents.reference.oneStackToQuantizer()
            BarrelTransactionAmount.ALL -> TODO()
        }

        val initialAmount = storage.contents.amount
        storage.removeAtMost(removedItems).toObjects().forEach { it.giveTo(player) }
        if (storage.contents.amount != initialAmount) {
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 0.8f)
        }

        markDirty()
    }

    override fun takeFromPlayer(player: PlayerEntity, amount: BarrelTransactionAmount) {
        val initialAmount = storage.contents.amount
        when (amount) {
            BarrelTransactionAmount.ONE -> TODO()

            BarrelTransactionAmount.MANY -> {
                if (!player.mainHandStack.isEmpty) {
                    val result = storage.addAtMost(player.mainHandStack.toQuantifier()).firstStack()

                    player.setStackInHand(
                        Hand.MAIN_HAND,
                        result
                    )
                }
            }

            BarrelTransactionAmount.ALL -> {
                player.inventory.main.replaceAll { storage.addAtMost(it.toQuantifier()).firstStack() }
            }
        }

        if (initialAmount != storage.contents.amount) {
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 0.65f)
        }

        player.inventory.markDirty()
        markDirty()
    }

    override fun toClientTag(tag: CompoundTag?): CompoundTag = requireNotNull(tag).apply {
        put(STORED_ITEM_TAG, storage.contents.reference.toTag(CompoundTag()))
        putLong(AMOUNT_STORED_TAG, storage.contents.amount)
        putBoolean(CLEAR_WHEN_EMPTY_TAG, clearWhenEmpty)
        put(UPGRADE_TAG, appliedUpgrades.mapNotNull { u -> UpgradeRegistry.writeUpgrade(u) }.toCollection(ListTag()))

        putLong(PARENT_POS_TAG, parentPos?.asLong() ?: 0L)
        putLong(REFERRER_POS_TAG, parentPos?.asLong() ?: 0L)
    }

    override fun fromClientTag(tag: CompoundTag?) = requireNotNull(tag).run {
        // Upgrades
        maxUpgrades = if (contains(MAX_UPGRADES_TAG)) {
            getInt(MAX_UPGRADES_TAG)
        } else {
            DEFAULT_MAX_UPGRADES
        }

        appliedUpgrades = getList(UPGRADE_TAG, NbtType.COMPOUND)
            .take(maxUpgrades)
            .mapNotNull { t -> UpgradeRegistry.readUpgrade(t as? CompoundTag ?: return@mapNotNull null) }

        // State
        handleUpgradeChanges()
        clearWhenEmpty = getBoolean(CLEAR_WHEN_EMPTY_TAG)

        // Contents
        val item = ItemStack.fromTag(getCompound(STORED_ITEM_TAG))
        if (item.isEmpty) {
            storage.contents = ItemStackQuantifier.NONE
        } else {
            val itemAmount = min(max(0L, getLong(AMOUNT_STORED_TAG)), storage.capacity)
            storage.contents = ItemStackQuantifier(item.withCount(1), itemAmount)
        }

        // Connections
        val parentPosL = getLong(PARENT_POS_TAG)
        val referrerPosL = getLong(REFERRER_POS_TAG)

        parentPos = if (parentPosL != 0L) BlockPos.fromLong(parentPosL) else null
        referrerPos = if (referrerPosL != 0L) BlockPos.fromLong(referrerPosL) else null
    }

    // Delegation of Inventory to invAttribute. As far as I know we can't use Kotlin's implementation by delegation
    // because the implementation can change.

    override fun getInvStack(slot: Int): ItemStack = invWrapper.getInvStack(slot)

    override fun clear() = invWrapper.clear()

    override fun setInvStack(slot: Int, stack: ItemStack?) = invWrapper.setInvStack(slot, stack)

    override fun removeInvStack(slot: Int): ItemStack = invWrapper.removeInvStack(slot)

    override fun canPlayerUseInv(player: PlayerEntity?): Boolean = invWrapper.canPlayerUseInv(player)

    override fun getInvSize(): Int = invWrapper.invSize

    override fun takeInvStack(slot: Int, amount: Int): ItemStack = invWrapper.takeInvStack(slot, amount)

    override fun isInvEmpty(): Boolean = invWrapper.isInvEmpty

    override fun isValidInvStack(slot: Int, stack: ItemStack?): Boolean = invWrapper.isValidInvStack(slot, stack)

    // Parenting
    public fun updateAndPropagateParents(seen: MutableSet<BlockPos> = mutableSetOf()) {
        if (world == null) return

        updateParents()
        seen += pos

        pos.neighbors.mapNotNull { p ->
            world?.getBlockEntity(p) as? ItemBarrelBlockEntity
        }.forEach { b ->
            if (b.parentPos == null && b.pos !in seen) {
                seen += b.pos
                b.updateAndPropagateParents(seen)
            }
        }
    }

    public fun updateParents() {
        if (world == null) return

        parent?.removeChild(pos)

        val neighboringControllers = pos.neighbors.mapNotNull { p ->
            world?.getBlockEntity(p) as? BarrelControllerBlockEntity
        }

        val neighboringBarrels = pos.neighbors.mapNotNull { p ->
            world?.getBlockEntity(p) as? ItemBarrelBlockEntity
        }.filter { b -> b.parentPos != null }

        val allParents =
            neighboringControllers.mapNotNull { c -> c.pos } +
                    neighboringBarrels.mapNotNull { b -> b.parentPos }

        if (allParents.distinct().size > 1) {
            world?.breakBlock(pos, true)
            return
        }

        if (neighboringControllers.size == 1) {
            val controller = neighboringControllers.first()
            parentPos = controller.pos
            referrerPos = parentPos

        } else {
            if (neighboringBarrels.isEmpty()) {
                return
            }

            val referrer = neighboringBarrels.first()
            parentPos = referrer.parentPos
            referrerPos = referrer.pos
        }

        parent?.addChild(pos)
    }

    val parent: BarrelControllerBlockEntity?
        get() = if (parentPos == null) null else world?.getBlockEntity(parentPos) as? BarrelControllerBlockEntity
}
