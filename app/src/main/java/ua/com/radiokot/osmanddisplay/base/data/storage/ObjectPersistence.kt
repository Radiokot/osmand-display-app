package ua.com.radiokot.osmanddisplay.base.data.storage

interface ObjectPersistence<T> {
    fun loadItem(): T?
    fun saveItem(item: T)
    fun hasItem(): Boolean
    fun clear()
}