package ua.com.radiokot.osmanddisplay.base.data.storage

import android.content.SharedPreferences
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Implements persistence for an object of type [T]
 * based on [SharedPreferences] with [ObjectMapper] serialization
 *
 * @see forType
 */
open class SharedPreferencesObjectPersistence<T : Any>(
    protected open val objectMapper: ObjectMapper,
    protected open val itemClass: Class<T>,
    protected open val preferences: SharedPreferences,
    protected open val key: String
) : ObjectPersistence<T> {
    protected open var loadedItem: T? = null

    override fun loadItem(): T? {
        return loadedItem
            ?: preferences
                .getString(key, null)
                ?.let(this::deserializeItem)
                ?.also { loadedItem = it }
    }

    override fun saveItem(item: T) {
        loadedItem = item
        preferences
            .edit()
            .putString(key, serializeItem(item))
            .apply()
    }

    override fun hasItem(): Boolean {
        return loadItem() != null
    }

    override fun clear() {
        loadedItem = null
        preferences
            .edit()
            .remove(key)
            .apply()
    }

    protected open fun serializeItem(item: T): String =
        objectMapper.writeValueAsString(item)

    protected open fun deserializeItem(serialized: String): T? =
        try {
            objectMapper.readValue(serialized, itemClass)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    companion object {
        inline fun <reified T : Any> forType(
            objectMapper: ObjectMapper,
            preferences: SharedPreferences,
            key: String
        ) =
            SharedPreferencesObjectPersistence(objectMapper, T::class.java, preferences, key)
    }
}