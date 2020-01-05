package me.branchpanic.mods.stockpile.api.multiblock

interface MultiblockParent<T> {
    val children: List<T>
}
