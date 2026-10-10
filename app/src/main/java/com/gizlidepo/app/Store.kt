package com.gizlidepo.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

enum class T { FOLDER, NOTE, FILE, APP }

// data: NOTE=metin, FILE=saklanan dosya adi, APP=paket adi
data class Item(val id: String, val parent: String, val type: T, val name: String, val data: String)

fun uid() = UUID.randomUUID().toString()

class Store(ctx: Context) {
    private val c = ctx.applicationContext
    private val p = c.getSharedPreferences("vault", 0)
    val dir = File(c.filesDir, "vault").apply { mkdirs() }

    private fun hash(s: String): String {
        val salt = p.getString("salt", null) ?: uid().also { p.edit().putString("salt", it).apply() }
        return MessageDigest.getInstance("SHA-256").digest((salt + s).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    val hasAuth get() = p.contains("h")
    val mode get() = p.getString("mode", "pin")!!
    var dark: Boolean
        get() = p.getBoolean("dark", false)
        set(v) { p.edit().putBoolean("dark", v).apply() }

    /** Sifreyi kaydeder, kurtarma kodunu dondurur */
    fun setAuth(mode: String, secret: String): String {
        val code = (10000000..99999999).random().toString()
        p.edit().putString("mode", mode).putString("h", hash(secret)).putString("rh", hash(code)).apply()
        return code
    }
    fun check(s: String) = hash(s) == p.getString("h", "")
    fun checkRecovery(s: String) = hash(s.trim()) == p.getString("rh", "")

    fun load(): List<Item> {
        val a = JSONArray(p.getString("items", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Item(o.getString("id"), o.getString("p"), T.valueOf(o.getString("t")), o.getString("n"), o.getString("d"))
        }
    }
    fun save(l: List<Item>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("p", it.parent).put("t", it.type.name).put("n", it.name).put("d", it.data)) }
        p.edit().putString("items", a.toString()).apply()
    }

    fun remove(all: List<Item>, item: Item): List<Item> {
        val ids = mutableSetOf(item.id)
        var changed = true
        while (changed) {
            changed = false
            all.filter { it.parent in ids && it.id !in ids }.forEach { ids.add(it.id); changed = true }
        }
        all.filter { it.id in ids && it.type == T.FILE }.forEach { File(dir, it.data).delete() }
        return all.filter { it.id !in ids }
    }

    /** Dosyayi uygulamanin ozel alanina kopyalar (galeride gorunmez) */
    fun import(uri: Uri, parent: String): Item? {
        var name = "dosya"
        c.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = it.getString(i)
            }
        }
        val ext = name.substringAfterLast('.', "")
        val stored = uid() + if (ext.isEmpty()) "" else ".$ext"
        return try {
            c.contentResolver.openInputStream(uri)?.use { i ->
                File(dir, stored).outputStream().use { o -> i.copyTo(o) }
            } ?: return null
            Item(uid(), parent, T.FILE, name, stored)
        } catch (e: Exception) { null }
    }
}
