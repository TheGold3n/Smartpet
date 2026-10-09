package cl.inacap.smartpet.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.datos.BaseDatos

@Composable
fun DialogoCambiarCredenciales(
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { BaseDatos(context) }

    var nuevoUsuario by remember { mutableStateOf(Sesion.usuarioActual) }
    var nuevaPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var errorMensaje by remember { mutableStateOf("") }
    var mostrarPass by remember { mutableStateOf(false) }

    val petOrange = Color(0xFFFF8A3D)
    val petBrown = Color(0xFF4A3428)
    val petRed = Color(0xFFE53935)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = petOrange,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Cambiar Credenciales",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = petBrown
                    )
                    Text(
                        text = "Configuración de Acceso Inicial (Uso Único)",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Personalice su nombre de usuario y contraseña para asegurar la estación SmartPet.",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )

                OutlinedTextField(
                    value = nuevoUsuario,
                    onValueChange = { nuevoUsuario = it },
                    label = { Text("Nuevo Usuario") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = petOrange) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nuevaPass,
                    onValueChange = { nuevaPass = it },
                    label = { Text("Nueva Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = petOrange) },
                    trailingIcon = {
                        IconButton(onClick = { mostrarPass = !mostrarPass }) {
                            Icon(
                                imageVector = if (mostrarPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                    },
                    visualTransformation = if (mostrarPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it },
                    label = { Text("Confirmar Nueva Contraseña") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = petOrange) },
                    visualTransformation = if (mostrarPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMensaje.isNotEmpty()) {
                    Text(
                        text = errorMensaje,
                        color = petRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nuevoUsuario.trim().isEmpty()) {
                        errorMensaje = "El nombre de usuario no puede estar vacío"
                        return@Button
                    }
                    if (nuevaPass.length < 4) {
                        errorMensaje = "La contraseña debe tener al menos 4 caracteres"
                        return@Button
                    }
                    if (nuevaPass != confirmPass) {
                        errorMensaje = "Las contraseñas no coinciden"
                        return@Button
                    }

                    val ok = db.cambiarCredenciales(Sesion.usuarioActual, nuevoUsuario.trim(), nuevaPass)
                    if (ok) {
                        val prefs = context.getSharedPreferences("smartpet_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("credenciales_personalizadas", true).apply()
                        Sesion.usuarioActual = nuevoUsuario.trim()
                        Toast.makeText(context, "✅ Usuario y contraseña actualizados", Toast.LENGTH_SHORT).show()
                        onSuccess(nuevoUsuario.trim())
                    } else {
                        errorMensaje = "No se pudieron actualizar las credenciales"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = petOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar Cambios", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = petBrown)
            }
        }
    )
}
