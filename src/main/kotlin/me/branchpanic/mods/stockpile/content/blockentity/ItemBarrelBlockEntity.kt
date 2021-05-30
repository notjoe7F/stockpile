package me.branchpanic.mods.stockpile.content.blockentity

import me.branchpanic.mods.stockpile.api.AbstractBarrelBlockEntity
import me.branchpanic.mods.stockpile.api.BarrelTransactionAmount
import me.branchpanic.mods.stockpile.api.upgrade.Upgrade
import me.branchpanic.mods.stockpile.api.upgrade.UpgradeContainer
import me.branchpanic.mods.stockpile.api.upgrade.UpgradeRegistry
import me.branchpanic.mods.stockpile.api.upgrade.barrel.ItemBarrelUpgrade
import me.branchpanic.mods.stockpile.content.block.ItemBarrelBlock
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
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

class ItemBarrelBlockEntity(
    override var appliedUpgrades: List<Upgrade> = emptyList(),
    override var maxUpgrades: Int = DEFAULT_MAX_UPGRADES
) : AbstractBarrelBlockEntity<ItemStack>(
    storage = MassItemStackStorage(ItemStackQuantifier.NONE, DEFAULT_CAPACITY_STACKS),
    clearWhenEmpty = true,
    doubleClickThresholdMs = 1000,
    type = TYPE
), BlockEntityClientSerializable, Inventory, UpgradeContainer {

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

    companion object {
        const val DEFAULT_CAPACITY_STACKS = 32
        const val DEFAULT_MAX_UPGRADES = 6

        const val STORED_ITEM_TAG = "StoredItem"
        const val AMOUNT_STORED_TAG = "AmountStored"
        const val CLEAR_WHEN_EMPTY_TAG = "ClearWhenEmpty"
        const val UPGRADE_TAG = "Upgrades"
        const val MAX_UPGRADES_TAG = "MaxUpgrades"

        const val STORED_BLOCK_ENTITY_TAG = "StoredBlockEntity"

        val TYPE: BlockEntityType<ItemBarrelBlockEntity> =
            BlockEntityType.Builder.create(Supplier { ItemBarrelBlockEntity() }, ItemBarrelBlock).build(null)

        fun fromStack(stack: ItemStack): ItemBarrelBlockEntity {
            val barrel = ItemBarrelBlockEntity()
            barrel.fromClientTag(stack.getOrCreateSubTag(STORED_BLOCK_ENTITY_TAG))
            return barrel
        }
    }

    val invAttribute = FixedMassItemInv(storage, false)
    private val invWrapper = UnrestrictedInventoryFixedWrapper(invAttribute)

    init {
        invAttribute.addListener({ markDirty() }, { })
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

    // TODO: Delegate to observable?
    private fun handleUpgradeChanges() {
        invAttribute.voidExtraItems = appliedUpgrades.filterIsInstance<TrashUpgrade>().any()

        // TODO: Lame
        (storage as MassItemStackStorage).maxStacks = appliedUpgrades.filterIsInstance<ItemBarrelUpgrade>()
            .fold(DEFAULT_CAPACITY_STACKS) { acc, upgrade -> upgrade.upgradeMaxStacks(acc) }
    }

    override fun giveToPlayer(player: PlayerEntity, amount: BarrelTransactionAmount) {
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
            val itemAmount = min(
                max(0L, getLong(AMOUNT_STORED_TAG)),
                (item.maxCount * (storage as MassItemStackStorage).maxStacks).toLong())
            storage.contents = ItemStackQuantifier(item.withCount(1), itemAmount)
        }
    }

    // Delegation of Inventory to invAttribute. As far as I know we can't use Kotlin's implementation by delegation
    // because the implementation can change.

    override fun getStack(slot: Int): ItemStack = invWrapper.getStack(slot)

    override fun clear() = invWrapper.clear()

    override fun setStack(slot: Int, stack: ItemStack?) = invWrapper.setStack(slot, stack)

    override fun removeStack(slot: Int): ItemStack = invWrapper.removeStack(slot)

    override fun canPlayerUse(player: PlayerEntity?): Boolean = invWrapper.canPlayerUse(player)

    override fun size(): Int = invWrapper.size()

    override fun removeStack(slot: Int, amount: Int): ItemStack = invWrapper.removeStack(slot, amount)

    override fun isEmpty(): Boolean = invWrapper.isEmpty

    override fun isValid(slot: Int, stack: ItemStack?): Boolean = invWrapper.isValid(slot, stack)
}
