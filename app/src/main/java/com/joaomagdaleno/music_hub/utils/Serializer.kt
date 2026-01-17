package com.joaomagdaleno.music_hub.utils

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Serializer {
    val json = Json {
        ignoreUnknownKeys = true
    }

    inline fun <reified T> toData(data: String) = runCatching {
        json.decodeFromString<T>(data)
    }.recoverCatching {
        throw DecodingException(data, it)
    }

    inline fun <reified T> toJson(value: T) = json.encodeToString(value)

    inline fun <reified T> putSerialized(bundle: Bundle, key: String, value: T) {
        bundle.putString(key, toJson(value))
    }

    inline fun <reified T> getSerialized(bundle: Bundle, key: String): Result<T>? {
        return bundle.getString(key)?.let { toData<T>(it) }
    }

    @Suppress("DEPRECATION")
    inline fun <reified T : Parcelable> getParcel(bundle: Bundle, key: String?) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            bundle.getParcelable(key, T::class.java)
        else bundle.getParcelable(key)

    fun getRootCause(throwable: Throwable): Throwable =
        generateSequence(throwable) { it.cause }.last()

    data class DecodingException(val json: String, val error: Throwable) : Exception(error)
}