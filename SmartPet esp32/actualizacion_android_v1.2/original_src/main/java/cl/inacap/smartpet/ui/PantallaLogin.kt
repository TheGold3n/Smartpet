package cl.inacap.smartpet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
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
import kotlinx.coroutines.delay

@Composable
fun PantallaLogin(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BaseDatos(context) }
    var usuario by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var mostrarPass by remember { mutableStateOf(false) }
    var mostrarDialogoCredenciales by remember { mutableStateOf(false) }

    var intentos by remember { mutableStateOf(0) }
    var bloqueadoHasta by remember { mutableStateOf(0L) }
    var segundosRestantes by remember { mutableStateOf(0) }

    LaunchedEffect(bloqueadoHasta) {
        if (bloqueadoHasta > 0) {
            while (System.currentTimeMillis() < bloqueadoHasta) {
                segundosRestantes = ((bloqueadoHasta - System.currentTimeMillis()) / 1000).toInt() + 1
                delay(1000)
            }
            intentos = 0
            bloqueadoHasta = 0
            segundosRestantes = 0
            mensaje = ""
        }
    }

    // Colores temáticos
    val warmBg = Color(0xFFFFF9F2)
    val petOrange = Color(0xFFFF8A3D)
    val petBrown = Color(0xFF4A3428)
    val petRed = Color(0xFFE53935)

    Scaffold(
        containerColor = warmBg,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Logo e ícono de mascota
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFFFE0B2),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = "Logo",
                            tint = petOrange,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SmartPet Station",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = petBrown
                )
                Text(
                    text = "Acceso Seguro al Sistema",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Tarjeta Principal de Formulario
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = usuario,
                            onValueChange = { usuario = it },
                            label = { Text("Usuario") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = petOrange)
                            },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Contraseña (mín. 8 caracteres)") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = petOrange)
                            },
                            trailingIcon = {
                                IconButton(onClick = { mostrarPass = !mostrarPass }) {
                                    Icon(
                                        imageVector = if (mostrarPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                            },
                            visualTransformation = if (mostrarPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (mensaje.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Text(
                                    text = mensaje,
                                    color = petRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (bloqueadoHasta > System.currentTimeMillis()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⏳ Bloqueado por seguridad: reintente en $segundosRestantes s",
                                    color = petRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else {
                            // Botón Ingresar
                            Button(
                                onClick = {
                                    if (pass.length < 8) {
                                        mensaje = "La contraseña debe tener al menos 8 caracteres"
                                        return@Button
                                    }
                                    val rol = db.autenticar(usuario, pass)
                                    if (rol != null) {
                                        Sesion.usuarioActual = usuario
                                        Sesion.setRol(rol)
                                        onLoginSuccess()
                                    } else {
                                        intentos++
                                        mensaje = "Credenciales incorrectas ($intentos/5)"
                                        if (intentos >= 5) {
                                            bloqueadoHasta = System.currentTimeMillis() + 30000
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = petOrange),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Ingresar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            // Botón Registrar
                            OutlinedButton(
                                onClick = {
                                    if (pass.length < 8) {
                                        mensaje = "La contraseña debe tener al menos 8 caracteres"
                                        return@OutlinedButton
                                    }
                                    if (db.registrar(usuario, pass)) {
                                        mensaje = "✅ Usuario registrado. Ahora presiona Ingresar."
                                    } else {
                                        mensaje = "El usuario ya existe o hubo un error."
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = petBrown),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Registrar Nuevo Usuario", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Botón de uso único para cambiar usuario y contraseña
                            TextButton(
                                onClick = { mostrarDialogoCredenciales = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔑 Cambiar Usuario y Contraseña (Uso Único)",
                                    fontSize = 13.sp,
                                    color = petOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Margen generoso al fondo para que nada quede al ras de la pantalla
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    if (mostrarDialogoCredenciales) {
        DialogoCambiarCredenciales(
            onDismiss = { mostrarDialogoCredenciales = false },
            onSuccess = { nuevoUser ->
                usuario = nuevoUser
                mensaje = "✅ Credenciales actualizadas. Ingresa con tu nueva clave."
                mostrarDialogoCredenciales = false
            }
        )
    }
}
