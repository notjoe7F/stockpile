package notjoe.stockpile.blockentity

import java.text.NumberFormat
import java.util.UUID

import net.minecraft.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.text.{TextComponent, TranslatableTextComponent}
import notjoe.cereal.serialization.Persistent
import notjoe.stockpile.inventory.MassItemInventory

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object CrateBlockEntity {
  final val TYPE = BlockEntityType.Builder.create[CrateBlockEntity](() => new CrateBlockEntity).method_11034(null)
}

class CrateBlockEntity extends BlockEntity(CrateBlockEntity.TYPE)
  with AutoPersistence
  with Inventory {

  final val DOUBLE_CLICK_PERIOD_MS = 500

  @Persistent var inventory = new MassItemInventory(onChanged = () => markDirty())

  private var playerRightClickTimers: Map[UUID, Long] = Map.empty

  def onLeftClick(player: PlayerEntity): Unit = {
    val extractedStack = inventory.takeInvStack(MassItemInventory.OUTPUT_SLOT,
      if (player.isSneaking) inventory.stackSize else 1)

    if (!extractedStack.isEmpty) {
      player.inventory.insertStack(extractedStack)
      world.spawnEntity(new ItemEntity(world, player.x, player.y, player.z, extractedStack))
      world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCK, 0.1f, 0.7f)
      displayContentInfo(player)
    }
  }

  /**
    * Handles a right-click from a specified PlayerEntity.
    *
    * @param player Player that interacted with this crate.
    */
  def onRightClick(player: PlayerEntity): Unit = {
    playerRightClickTimers = playerRightClickTimers
      .filter { case (_, time) => System.currentTimeMillis() - time <= DOUBLE_CLICK_PERIOD_MS }

    if (player.isSneaking) {
      toggleEmptyBehavior(player)
      return
    }

    if (playerInteractedTwice(player) && !inventory.isAcceptingNewStackType) {
      insertAllStacksFromInventory(player)
    } else {
      recordPlayerInteraction(player)
      insertActiveStack(player)
    }

    displayContentInfo(player)
  }

  def toggleEmptyBehavior(player: PlayerEntity): Unit = {
    inventory.allowNewStackWhenEmpty = !inventory.allowNewStackWhenEmpty

    if (inventory.isInvEmpty && inventory.allowNewStackWhenEmpty) {
      inventory.clearInv()
    }

    if (inventory.allowNewStackWhenEmpty) {
      world.playSound(null, pos, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF, SoundCategory.BLOCK, 0.1f, 0.9f)
      player.addChatMessage(new TranslatableTextComponent("stockpile.crate.just_unlocked"), true)
    } else {
      world.playSound(null, pos, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON, SoundCategory.BLOCK, 0.1f, 0.9f)
      player.addChatMessage(new TranslatableTextComponent("stockpile.crate.just_locked"), true)
    }

    markDirty()
  }

  def recordPlayerInteraction(player: PlayerEntity): Unit = {
    playerRightClickTimers += (player.getUuid -> System.currentTimeMillis())
  }

  def playerInteractedTwice(player: PlayerEntity): Boolean = playerRightClickTimers.contains(player.getUuid)

  /**
    * Inserts the stack a player is holding, updating it in-place.
    *
    * @param player Player to insert held stack from.
    */
  def insertActiveStack(player: PlayerEntity): Unit = {
    val activeHand = player.getActiveHand
    val activeStackBeforeInsert = player.getStackInHand(activeHand)
    val resultingStack = inventory.insertStack(activeStackBeforeInsert)
    player.setStackInHand(activeHand, resultingStack)
  }

  /**
    * Inserts all possible stacks from a player's inventory, updating it in-place.
    *
    * @param player Player to insert all stacks from.
    */
  def insertAllStacksFromInventory(player: PlayerEntity): Unit = {
    player.inventory.main.replaceAll(inventory.insertStack)
    player.inventory.offHand.replaceAll(inventory.insertStack)
    player.inventory.markDirty()
  }

  /**
    * Displays information about this crate to a specified player.
    *
    * @param player Player to display information to.
    */
  def displayContentInfo(player: PlayerEntity): Unit = {
    if (inventory.isInvEmpty) {
      player.addChatMessage(new TranslatableTextComponent("stockpile.crate.empty"), true)
    } else {
      val formatter = NumberFormat.getInstance()

      player.addChatMessage(new TranslatableTextComponent(
        "stockpile.crate.contents_world",
        formatter.format(inventory.amountStored),
        formatter.format(inventory.maxStacks * inventory.stackSize),
        inventory.stackType.getDisplayName,
        formatter.format(inventory.amountStored / inventory.stackSize),
        formatter.format(inventory.maxStacks)
      ), true)
    }
  }

  override def toString: String = s"CrateBlockEntity{inventory=$inventory,}"

  override def canPlayerUseInv(playerEntity: PlayerEntity): Boolean = playerEntity.squaredDistanceTo(pos) < 12 * 12

  // Delegation of Inventory to MassItemInventory

  override def getInvSize: Int = inventory.getInvSize

  override def isInvEmpty: Boolean = inventory.isInvEmpty

  override def getInvStack(i: Int): ItemStack = inventory.getInvStack(i)

  override def takeInvStack(i: Int, i1: Int): ItemStack = inventory.takeInvStack(i, i1)

  override def removeInvStack(i: Int): ItemStack = inventory.removeInvStack(i)

  override def setInvStack(i: Int, itemStack: ItemStack): Unit = inventory.setInvStack(i, itemStack)

  override def getName: TextComponent = inventory.getName

  override def clearInv(): Unit = inventory.clearInv()
}