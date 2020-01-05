package me.branchpanic.mods.stockpile.api.multiblock

interface ParentComponent<T> {
    val isParented: Boolean
    val parent: T?
    val referrer: T?
}