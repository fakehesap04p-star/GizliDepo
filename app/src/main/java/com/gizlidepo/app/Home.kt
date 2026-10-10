package com.gizlidepo.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap)

fun loadApps(c: Context): List<AppInfo> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.distinct()
        .filter { it != c.packageName }
        .mapNotNull {
            runCatching {
                AppInfo(pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString(), it,
                    pm.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap())
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
}

@Composable
fun Home(s: Store, dark: Boolean, onDark: (Boolean) -> Unit, onChangePw: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var all by remember { mutableStateOf(s.load()) }
    var stack by remember { mutableStateOf(listOf<Item>()) }
    var dlg by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Item?>(null) }
    var del by remember { mutableStateOf<Item?>(null) }
    var settings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val cur = stack.lastOrNull()?.id ?: "root"
    fun commit(l: List<Item>) { all = l; s.save(l) }

    BackHandler(settings || stack.isNotEmpty()) { if (settings) settings = false else stack = stack.dropLast(1) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                val n = withContext(Dispatchers.IO) { uris.mapNotNull { s.import(it, cur) } }
                commit(all + n); busy = false
                Toast.makeText(ctx, "${n.size} dosya eklendi. Orijinalleri galeriden silmeyi unutma.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun open(m: Item) {
        when (m.type) {
            T.FOLDER -> stack = stack + m
            T.NOTE -> edit = m
            T.APP -> {
                val i = ctx.packageManager.getLaunchIntentForPackage(m.data)
                if (i != null) ctx.startActivity(i) else Toast.makeText(ctx, "Uygulama bulunamadı", Toast.LENGTH_SHORT).show()
            }
            T.FILE -> {
                val f = File(s.dir, m.data)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fp", f)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
                Session.skip = true
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(ctx, "Bu dosyayı açacak uygulama yok", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (settings) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ settings = false }) { Ico(Ic.back) }
                Text("Ayarlar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text("Tema", color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeCard("Aydınlık", Ic.sun, !dark) { onDark(false) }
                ThemeCard("Karanlık", Ic.moon, dark) { onDark(true) }
            }
            Spacer(Modifier.height(24.dp))
            Card1(onClick = onChangePw) {
                Ico(Ic.lock, cs.primary); Spacer(Modifier.width(12.dp))
                Text("Şifreyi / giriş yöntemini değiştir", fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stack.isNotEmpty()) IconButton({ stack = stack.dropLast(1) }) { Ico(Ic.back) }
            Text(
                stack.lastOrNull()?.name ?: "Gizli Depo", Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1
            )
            IconButton({ dlg = "menu" }) { Ico(Ic.add, cs.primary) }
            if (stack.isEmpty()) {
                IconButton({ settings = true }) { Ico(Ic.settings) }
                IconButton({ Session.unlocked = false }) { Ico(Ic.lock) }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val list = all.filter { it.parent == cur }.sortedWith(compareBy({ it.type != T.FOLDER }, { it.name.lowercase() }))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(Ic.folder, cs.onSurfaceVariant, 56.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Burası boş. + ile bir şeyler ekle.", color = cs.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { m -> ItemRow(m, { open(m) }, { del = m }) }
            }
        }
    }

    when (dlg) {
        "menu" -> AlertDialog(
            onDismissRequest = { dlg = "" }, confirmButton = {}, title = { Text("Ekle") },
            text = {
                Column {
                    MenuRow(Ic.file, "Dosya ekle") { dlg = ""; Session.skip = true; pick.launch("*/*") }
                    MenuRow(Ic.note, "Not ekle") { dlg = "note" }
                    MenuRow(Ic.folder, "Klasör oluştur") { dlg = "folder" }
                    MenuRow(Ic.apps, "Uygulama ekle") { dlg = "apps" }
                }
            })
        "folder" -> TextDlg("Klasör adı", { dlg = "" }) { n -> commit(all + Item(uid(), cur, T.FOLDER, n, "")); dlg = "" }
        "note" -> NoteDlg("", "", { dlg = "" }) { t, x -> commit(all + Item(uid(), cur, T.NOTE, t, x)); dlg = "" }
        "apps" -> AppsDlg({ dlg = "" }) { a -> commit(all + Item(uid(), cur, T.APP, a.label, a.pkg)); dlg = "" }
    }
    edit?.let { e ->
        NoteDlg(e.name, e.data, { edit = null }) { t, x ->
            commit(all.map { if (it.id == e.id) it.copy(name = t, data = x) else it }); edit = null
        }
    }
    del?.let { d ->
        AlertDialog(
            onDismissRequest = { del = null }, title = { Text("Silinsin mi?") },
            text = { Text("\"${d.name}\" kalıcı olarak silinecek.") },
            confirmButton = { TextButton({ commit(s.remove(all, d)); del = null }) { Text("Sil", color = Color(0xFFE5484D)) } },
            dismissButton = { TextButton({ del = null }) { Text("Vazgeç") } })
    }
}

@Composable
fun ItemRow(m: Item, onClick: () -> Unit, onDel: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val appIcon = if (m.type == T.APP) remember(m.data) {
        runCatching { ctx.packageManager.getApplicationIcon(m.data).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    } else null
    Card1(onClick = onClick) {
        if (appIcon != null) Image(appIcon, null, Modifier.size(42.dp))
        else Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Ico(when (m.type) { T.FOLDER -> Ic.folder; T.NOTE -> Ic.note; T.FILE -> Ic.file; T.APP -> Ic.apps }, cs.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                when (m.type) {
                    T.FOLDER -> "Klasör"; T.APP -> "Uygulama"
                    T.NOTE -> m.data.replace("\n", " ").take(40).ifEmpty { "Not" }
                    T.FILE -> m.name.substringAfterLast('.', "dosya").uppercase()
                }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1
            )
        }
        IconButton(onDel) { Ico(Ic.delete, cs.onSurfaceVariant) }
    }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(icon, MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RowScope.ThemeCard(label: String, icon: ImageVector, sel: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.colorScheme.primary
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(2.dp, if (sel) p else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Ico(icon, if (sel) p else MaterialTheme.colorScheme.onSurface, 32.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun TextDlg(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
        confirmButton = { TextButton({ if (t.isNotBlank()) onOk(t.trim()) }) { Text("Oluştur") } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } })
}

@Composable
fun NoteDlg(title0: String, text0: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var t by remember { mutableStateOf(title0) }
    var x by remember { mutableStateOf(text0) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Not") },
        text = {
            Column {
                OutlinedTextField(t, { t = it }, label = { Text("Başlık") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(x, { x = it }, label = { Text("Not") }, minLines = 5, maxLines = 10, shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = { TextButton({ onSave(t.ifBlank { "Başlıksız not" }.trim(), x) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } })
}

@Composable
fun AppsDlg(onDismiss: () -> Unit, onPick: (AppInfo) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Uygulama seç") },
        text = {
            val l = apps
            if (l == null) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(l, key = { it.pkg }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(a) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(a.icon, null, Modifier.size(40.dp)); Spacer(Modifier.width(12.dp)); Text(a.label, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Kapat") } })
}
