package com.messark.hawkerrush.utils

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.gson.Gson
import com.messark.hawkerrush.model.Settings
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    private val gson = Gson()

    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(input: InputStream): Settings {
        try {
            return gson.fromJson(input.readBytes().decodeToString(), Settings::class.java) ?: defaultValue
        } catch (e: Exception) {
            throw CorruptionException("Cannot read Settings", e)
        }
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(gson.toJson(t).toByteArray())
    }
}

val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings.json",
    serializer = SettingsSerializer
)

class SettingsRepository(private val context: Context) {
    val settingsFlow: Flow<Settings> = context.settingsDataStore.data

    suspend fun updateSettings(transform: suspend (Settings) -> Settings) {
        context.settingsDataStore.updateData(transform)
    }
}
