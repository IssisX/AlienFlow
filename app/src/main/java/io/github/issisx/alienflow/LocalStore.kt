package io.github.issisx.alienflow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(private val context: Context) {
    companion object {
        /** Live recognition stays on; optional polish runs once after Stop by default. */
        const val DEFAULT_LIVE_CLEANUP = false
    }
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val audioDir = File(context.filesDir, "pending-audio").apply { mkdirs() }
    private val historyFile = AtomicFile(File(context.filesDir, "history.json"))
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("alienflow-api", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("alienflow-api", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveKey(value: String) {
        if (value.isBlank()) { prefs.edit().remove("api_key").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("api_key", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "Key storage failed" }
    }
    fun apiKey(): String? {
        val stored = prefs.getString("api_key", null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    @Synchronized fun history(): List<JSONObject> {
        if (!historyFile.baseFile.exists()) return emptyList()
        val array = JSONArray(historyFile.readFully().toString(Charsets.UTF_8))
        val days = prefs.getInt("retention", 7)
        val cutoff = System.currentTimeMillis() - days * 86400000L
        val entries = (0 until array.length()).map { array.getJSONObject(it) }
        val kept = entries.filter { days == 0 || it.getLong("time") > cutoff }
        if (kept.size != entries.size) writeHistory(kept)
        return kept
    }
    @Synchronized fun saveResult(original: String, result: String, status: String): String? {
        if (prefs.getInt("retention", 7) == -1 || (original.isBlank() && result.isBlank())) return null
        val entries = history().toMutableList()
        val id = java.util.UUID.randomUUID().toString()
        entries.add(0, JSONObject().put("id", id).put("time", System.currentTimeMillis()).put("original", original).put("result", result).put("status", status))
        writeHistory(entries)
        return id
    }
    @Synchronized fun deleteHistory(id: String? = null) {
        val entries = history().toMutableList(); if (id == null) entries.clear() else entries.removeAll { it.optString("id") == id }
        writeHistory(entries)
    }
    private fun writeHistory(entries: List<JSONObject>) {
        val out = historyFile.startWrite()
        try { out.write(JSONArray(entries).toString().toByteArray()); historyFile.finishWrite(out) }
        catch (e: Exception) { historyFile.failWrite(out); throw e }
    }
    fun language() = prefs.getString("language", "en")!!.trim()
    fun mode() = prefs.getString("correction", "B")!!
    fun vocabularyForSession() = if (prefs.getBoolean("use_vocabulary", false)) prefs.getString("vocabulary", "").orEmpty() else ""
    fun pending(): List<File> {
        val cutoff = System.currentTimeMillis() - 86400000L
        return audioDir.listFiles()?.filter { file -> if ((context as? AlienApp)?.coordinator?.ownsAudio(file) == true) false else if (file.lastModified() < cutoff) { file.delete(); false } else file.extension == "wav" && file.length() > 44 }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
