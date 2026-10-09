package cl.inacap.smartpet.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.enlace.*

@Composable
fun PantallaPanel(onDisconnect: () -> Unit, onHistorial: () -> Unit) {
    val context = LocalContext.current
    val enlace by Sesion.enlaceActivo.collectAsState()
    val actual = enlace ?: return
    val estado by actual.estado.collectAsState()
    val detalle by actual.detalle.collectAsState()
    val acceso by actual.acceso.collectAsState()
    val rol by Sesion.rol.collectAsState()
    val comida by Sesion.nivelAlimento.collectAsState()
    val agua by Sesion.nivelAgua.collectAsState()
    val bomba by Sesion.bombaActiva.collectAsState()
    val dispensador by Sesion.dispensadorActivo.collectAsState()
    val pendiente by Sesion.pendiente.collectAsState()
    val mensaje by Sesion.mensaje.collectAsState()
    var gramos by remember { mutableFloatStateOf(30f) }
    var credenciales by remember { mutableStateOf(false) }
    var errorPermiso by remember { mutableStateOf("") }
    val permisoEnlace = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        errorPermiso = if (it) "Permiso concedido. Pulsa Reconectar." else "Permiso denegado. Revisa los permisos de SmartPet en los ajustes del teléfono."
    }
    val habilitado = Sesion.puedeControlar && !pendiente
    PetScreen {
        PetBrand("SmartPet Station", "Dispensador Inteligente")
        PetConnectionState(estado, detalle)
        Text(if (Sesion.medioWifi) "Nodo: ${Sesion.ultimaIp} · Puerto 5050" else "Conexión Bluetooth con el nodo emparejado")
        PetCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AccountCircle, null, tint = PetBrown)
                Text("Usuario: ${Sesion.usuarioActual} · Rol: $rol", fontWeight = FontWeight.SemiBold)
            }
        }
        if (acceso.isNotEmpty()) Text("Permiso otorgado por el nodo: $acceso")
        if (!Sesion.puedeControlar && estado == "Conectado") Text("Modo de solo lectura: las órdenes requieren cuenta operadora y PIN de control.")
        if (estado == "Desconectado") {
            Button(shape = RoundedCornerShape(16.dp), onClick = {
                val permiso = if (Sesion.medioWifi && Build.VERSION.SDK_INT >= 37) "android.permission.ACCESS_LOCAL_NETWORK"
                    else if (!Sesion.medioWifi && Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
                if (permiso != null && ContextCompat.checkSelfPermission(context, permiso) != PackageManager.PERMISSION_GRANTED) {
                    permisoEnlace.launch(permiso); return@Button
                }
                Sesion.desconectar()
                val nuevo = if (Sesion.medioWifi) WifiEnlace(Sesion.ultimoPin, false, Sesion.ultimaIp, rolLocal = Sesion.rol.value)
                else {
                    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                    val device = runCatching { adapter?.getRemoteDevice(Sesion.direccionBluetooth) }.getOrNull()
                    BluetoothEnlace(Sesion.ultimoPin, false, device, adapter, rolLocal = Sesion.rol.value)
                }
                Sesion.setEnlace(nuevo)
            }, modifier = Modifier.fillMaxWidth()) { Text("Reconectar") }
        }
        if (errorPermiso.isNotEmpty()) Text(errorPermiso, color = MaterialTheme.colorScheme.error)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PetReading("Comida en el plato", comida, false)
                Spacer(Modifier.height(8.dp))
                Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}", style = MaterialTheme.typography.titleMedium)
                Text("Ración simulada: ${gramos.toInt()} g")
                Slider(value = gramos, onValueChange = { gramos = it }, valueRange = 1f..60f, steps = 58, enabled = habilitado)
                Button(shape = RoundedCornerShape(16.dp), onClick = { Sesion.enviarComando("dispense", gramos.toInt().toString()) },
                    enabled = habilitado && comida != null && comida!! + gramos.toInt() <= 100, modifier = Modifier.fillMaxWidth()) {
                    Text("Dispensar ${gramos.toInt()} g")
                }
                Text("Capacidad del plato en esta simulación: 100 g.")
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PetReading("Estanque de Agua", agua, true)
                Spacer(Modifier.height(8.dp))
                Text("Bomba: ${if (bomba) "ENCENDIDA" else "APAGADA"}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = RoundedCornerShape(16.dp), onClick = { Sesion.enviarComando("pump", "ON") }, colors = ButtonDefaults.buttonColors(containerColor = PetBlue, contentColor = Color.White), enabled = habilitado && !bomba && (agua ?: 0.0) >= 20) { Text("Encender") }
                    OutlinedButton(shape = RoundedCornerShape(16.dp), onClick = { Sesion.enviarComando("pump", "OFF") }, enabled = habilitado && bomba) { Text("Apagar") }
                }
                Text("Se apaga con agua baja, pérdida de enlace o tras 60 segundos.")
                OutlinedButton(shape = RoundedCornerShape(16.dp), onClick = { Sesion.enviarComando("refill_water", "ON") }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0277BD), containerColor = Color(0xFFE1F5FE)), enabled = habilitado, modifier = Modifier.fillMaxWidth()) { Text("Simular relleno de agua al 95 %") }
            }
        }
        if (mensaje.isNotEmpty()) Text(mensaje, style = MaterialTheme.typography.titleSmall)
        Button(shape = RoundedCornerShape(16.dp), onClick = { Sesion.enviarComando("emergency_stop", "ON") }, enabled = Sesion.puedeControlar,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Detener actuadores") }
        OutlinedButton(shape = RoundedCornerShape(16.dp), onClick = onHistorial, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.History, null); Spacer(Modifier.width(8.dp)); Text("Ver historial de la simulación") }
        OutlinedButton(shape = RoundedCornerShape(16.dp), onClick = { credenciales = true }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar mi usuario y contraseña") }
        OutlinedButton(shape = RoundedCornerShape(16.dp), onClick = { Sesion.desconectar(); onDisconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar conexión") }
        TextButton(shape = RoundedCornerShape(16.dp), onClick = { Sesion.cerrarSesion() }) { Text("Cerrar sesión") }
    }
    if (credenciales) DialogoCambiarCredenciales(onDismiss = { credenciales = false }, onSuccess = { credenciales = false })
}

