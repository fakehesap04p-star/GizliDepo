package com.gizlidepo.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Ico(v: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurface, size: androidx.compose.ui.unit.Dp = 24.dp) =
    Icon(v, null, Modifier.size(size), tint)

@Composable
fun Card1(mod: Modifier = Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        mod.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

private fun nodeC(i: Int, w: Float) = Offset(w * ((i % 3) * 2 + 1) / 6f, w * ((i / 3) * 2 + 1) / 6f)

@Composable
fun PatternPad(mod: Modifier, onDone: (String) -> Unit) {
    var path by remember { mutableStateOf(listOf<Int>()) }
    var cur by remember { mutableStateOf<Offset?>(null) }
    var w by remember { mutableStateOf(1f) }
    val done by rememberUpdatedState(onDone)
    val pc = MaterialTheme.colorScheme.primary
    val dc = MaterialTheme.colorScheme.onSurface.copy(alpha = .25f)
    fun hit(o: Offset) {
        for (i in 0..8) if (i !in path && (nodeC(i, w) - o).getDistance() < w / 8f) path = path + i
    }
    Canvas(
        mod.aspectRatio(1f).onSizeChanged { w = it.width.toFloat() }.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { path = emptyList(); cur = it; hit(it) },
                onDrag = { c, _ -> cur = c.position; hit(c.position) },
                onDragEnd = { val r = path.joinToString("-"); path = emptyList(); cur = null; done(r) },
                onDragCancel = { path = emptyList(); cur = null }
            )
        }
    ) {
        for (k in 0 until path.size - 1) drawLine(pc, nodeC(path[k], w), nodeC(path[k + 1], w), 8f, StrokeCap.Round)
        val c = cur
        if (path.isNotEmpty() && c != null) drawLine(pc, nodeC(path.last(), w), c, 8f, StrokeCap.Round)
        for (i in 0..8) {
            val on = i in path
            drawCircle(if (on) pc else dc, if (on) 18f else 12f, nodeC(i, w))
            if (on) drawCircle(pc.copy(alpha = .2f), 42f, nodeC(i, w))
        }
    }
}

@Composable
fun SecretInput(mode: String, onSubmit: (String) -> Unit) {
    if (mode == "pattern") {
        PatternPad(Modifier.fillMaxWidth(0.85f), onSubmit)
    } else {
        var t by remember { mutableStateOf("") }
        OutlinedTextField(
            value = t,
            onValueChange = { t = if (mode == "pin") it.filter(Char::isDigit) else it },
            singleLine = true,
            label = { Text(if (mode == "pin") "PIN" else "Şifre") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (mode == "pin") KeyboardType.NumberPassword else KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit(t) }),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = { onSubmit(t) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Devam") }
    }
}

@Composable
fun AuthScreen(s: Store, setup: Boolean, onOk: () -> Unit) {
    var stage by remember { mutableStateOf(if (setup || !s.hasAuth) "choose" else "login") }
    var mode by remember { mutableStateOf(s.mode) }
    var first by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf("") }
    var fails by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var forgot by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    fun valid(v: String) = if (mode == "pattern") v.split("-").size >= 4 else v.length >= 4

    val onSet: (String) -> Unit = { v ->
        when {
            !valid(v) -> err = if (mode == "pattern") "En az 4 noktayı bağla" else "En az 4 karakter gir"
            first == null -> { first = v; err = "" }
            first == v -> { code = s.setAuth(mode, v); stage = "code"; err = "" }
            else -> { first = null; err = "Eşleşmedi, baştan dene" }
        }
    }
    val onLogin: (String) -> Unit = { v ->
        if (s.check(v)) onOk() else { fails++; err = "Yanlış, tekrar dene ($fails)" }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
            Ico(Ic.lock, cs.onPrimary, 36.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Gizli Depo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        when (stage) {
            "choose" -> {
                Text("Giriş yöntemini seç", color = cs.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                listOf("pattern" to "Desen", "pin" to "PIN", "password" to "Şifre (metin)").forEach { (k, l) ->
                    Card1(onClick = { mode = k; first = null; err = ""; stage = "set" }) {
                        Text(l, fontWeight = FontWeight.Medium, fontSize = 17.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            "set" -> {
                Text(if (first == null) "Yeni şifreni oluştur" else "Doğrulamak için tekrar gir", color = cs.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                key(first) { SecretInput(mode, onSet) }
            }
            "code" -> {
                Text("Kurtarma kodun", color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(code, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Şifreni unutursan bu kodla sıfırlayabilirsin. Güvenli bir yere yaz, bir daha gösterilmeyecek.",
                    textAlign = TextAlign.Center, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onOk, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Kaydettim") }
            }
            else -> {
                Text("Şifreni gir", color = cs.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                key(fails) { SecretInput(mode, onLogin) }
                if (fails >= 3) TextButton(onClick = { forgot = true }) { Text("Şifremi unuttum") }
            }
        }
        if (err.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(err, color = Color(0xFFE5484D))
        }
    }

    if (forgot) {
        var t by remember { mutableStateOf("") }
        var bad by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("Şifreyi sıfırla") },
            text = {
                Column {
                    Text("İlk kurulumda verilen 8 haneli kurtarma kodunu gir.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        t, { t = it.filter(Char::isDigit); bad = false }, singleLine = true, isError = bad,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        supportingText = { if (bad) Text("Kod yanlış") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (s.checkRecovery(t)) { forgot = false; fails = 0; err = ""; first = null; stage = "choose" } else bad = true
                }) { Text("Onayla") }
            },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text("Vazgeç") } }
        )
    }
}
