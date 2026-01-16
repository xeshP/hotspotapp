package com.hotspotapp

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

class PasswordFileReader(private val context: Context) {

    fun readPasswordsFromUri(uri: Uri): Result<List<String>> {
        return try {
            val passwords = mutableListOf<String>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val password = line?.trim()
                        if (!password.isNullOrEmpty()) {
                            passwords.add(password)
                        }
                    }
                }
            }

            if (passwords.isEmpty()) {
                Result.failure(Exception("No passwords found in file"))
            } else {
                Result.success(passwords)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFileName(uri: Uri): String {
        var name = "passwords.txt"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
