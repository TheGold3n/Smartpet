package cl.inacap.smartpet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.datos.BaseDatos
import kotlinx.coroutines.*

@Composable
fun DialogoCambiarCredenciales(onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nuevoUsuario by remember { mutableStateOf(Sesion.usuarioActual) }
    var anterior by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!ocupado) onDismiss() }, title = { Text("Cambiar mis credenciales") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nuevoUsuario, { nuevoUsuario = it.take(40) }, label = { Text("Usuario") }, singleLine = true)
                OutlinedTextField(anterior, { anterior = it }, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(nueva, { nueva = it }, label = { Text("Nueva contraseña (8 o más)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(confirmacion, { confirmacion = it }, label = { Text("Confirmar contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !ocupado, onClick = {
                if (!Sesion.autenticado.value || nuevoUsuario.trim().isEmpty() || nueva.length < 8 || nueva != confirmacion) {
                    error = "Revisa el usuario y las contraseñas; mínimo 8 caracteres"; return@TextButton
                }
                ocupado = true
                scope.launch {
                    val usuario = Sesion.usuarioActual
                    val ok = withContext(Dispatchers.IO) {
                        BaseDatos(context).use { it.cambiarCredenciales(usuario, nuevoUsuario.trim(), nueva, anterior) }
                    }
                    ocupado = false
                    if (ok) { Sesion.actualizarUsuario(nuevoUsuario.trim()); onSuccess(nuevoUsuario.trim()) }
                    else error = "Contraseña actual incorrecta o nombre de usuario ocupado"
                }
            }) { Text("Guardar") }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !ocupado) { Text("Cancelar") } })
}
