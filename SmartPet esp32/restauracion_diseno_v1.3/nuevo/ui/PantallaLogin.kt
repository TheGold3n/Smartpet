package cl.inacap.smartpet.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.datos.BaseDatos
import kotlinx.coroutines.*

@Composable
fun PantallaLogin(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("smartpet_login", Context.MODE_PRIVATE) }
    var usuario by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var bloqueo by remember { mutableLongStateOf(prefs.getLong("bloqueado_hasta", 0)) }
    var segundos by remember { mutableIntStateOf(0) }
    LaunchedEffect(bloqueo) {
        do {
            segundos = ((bloqueo - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0).toInt()
            if (segundos > 0) delay(500)
        } while (segundos > 0)
    }
    PetScreen {
        PetBrand("SmartPet Station", "Acceso Seguro al Sistema", centered = true)
        PetCard {
        Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("La primera cuenta de este teléfono es operadora. Las siguientes son observadoras.")
        OutlinedTextField(usuario, { usuario = it.take(40) }, shape = RoundedCornerShape(14.dp), label = { Text("Usuario") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PetOrange) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, shape = RoundedCornerShape(14.dp), label = { Text("Contraseña (mínimo 8 caracteres)") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PetOrange) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (segundos > 0) Text("Demasiados intentos. Espera $segundos segundos.", color = MaterialTheme.colorScheme.error)
        if (mensaje.isNotEmpty()) Text(mensaje)
        Button(shape = RoundedCornerShape(16.dp), onClick = {
            if (usuario.trim().isEmpty() || pass.length < 8) { mensaje = "Escribe usuario y contraseña de al menos 8 caracteres"; return@Button }
            ocupado = true
            scope.launch {
                val correcto = withContext(Dispatchers.IO) { Sesion.autenticar(usuario, pass) }
                ocupado = false
                if (correcto) {
                    prefs.edit().putInt("intentos", 0).putLong("bloqueado_hasta", 0).apply()
                    pass = ""
                    onLoginSuccess()
                } else {
                    val count = prefs.getInt("intentos", 0) + 1
                    mensaje = "Credenciales incorrectas"
                    if (count >= 5) {
                        bloqueo = System.currentTimeMillis() + 30_000
                        prefs.edit().putInt("intentos", 0).putLong("bloqueado_hasta", bloqueo).apply()
                    } else prefs.edit().putInt("intentos", count).apply()
                }
            }
        }, enabled = !ocupado && segundos == 0 && System.currentTimeMillis() >= bloqueo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (ocupado) "Verificando..." else "Ingresar") }
        OutlinedButton(shape = RoundedCornerShape(16.dp), onClick = {
            if (usuario.trim().isEmpty() || pass.length < 8) { mensaje = "Escribe usuario y contraseña de al menos 8 caracteres"; return@OutlinedButton }
            ocupado = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { BaseDatos(context).use { it.registrar(usuario.trim(), pass) } }
                ocupado = false
                mensaje = if (ok) "Cuenta creada. Pulsa Ingresar." else "El usuario ya existe o los datos no son válidos"
            }
        }, enabled = !ocupado && segundos == 0 && System.currentTimeMillis() >= bloqueo, modifier = Modifier.fillMaxWidth()) { Text("Registrar cuenta") }
    }
    }
}
