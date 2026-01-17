package com.joaomagdaleno.music_hub.utils.ui

import kotlinx.serialization.Serializable

@Serializable
data class ExceptionData(val title: String, val trace: String)
