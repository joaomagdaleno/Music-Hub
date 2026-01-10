package com.joaomagdaleno.music_hub.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val FILE_NAME = "music_hub_debug.txt"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            // Use public Documents folder for easy access
            @Suppress("DEPRECATION")
            val publicDocuments = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            
            if (!publicDocuments.exists()) {
                publicDocuments.mkdirs()
            }
            
            logFile = File(publicDocuments, FILE_NAME)
            
            // Create file if it doesn't exist
            if (!logFile!!.exists()) {
                logFile!!.createNewFile()
            }
            
            log("FileLogger", "Logger initialized. Path: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("FileLogger", "Failed to initialize logger: ${e.message}", e)
            // Fallback to internal storage if public fails
            try {
                logFile = File(context.filesDir, FILE_NAME)
                if (!logFile!!.exists()) logFile!!.createNewFile()
                log("FileLogger", "Logger fallback to internal: ${logFile?.absolutePath}")
            } catch (e2: Exception) {
                Log.e("FileLogger", "Fallback also failed", e2)
            }
        }
    }

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logMessage = StringBuilder().apply {
            append(timestamp).append(" [").append(tag).append("] ").append(message)
            throwable?.let {
                append("\n").append(Log.getStackTraceString(it))
            }
        }.toString()
        
        Log.d(tag, message)
        if (throwable != null) Log.e(tag, message, throwable)

        logFile?.let { file ->
            try {
                FileWriter(file, true).use { writer ->
                    writer.append(logMessage).append("\n")
                }
            } catch (e: IOException) {
                Log.e("FileLogger", "Failed to write log", e)
            }
        }
    }
    
    fun getLogPath(): String {
        return logFile?.absolutePath ?: "Not initialized"
    }
}
