package cl.inacap.smartpet.ui

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
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SmartPet Station", style = MaterialTheme.typography.headlineMedium)
        Text("Panel de Control · $estado")
        Text(if (Sesion.medioWifi) "Nodo: ${Sesion.ultimaIp} · Puerto 5050" else "Conexión Bluetooth con el nodo emparejado")
        if (detalle.isNotEmpty()) Text(detalle)
        Text("Usuario: ${Sesion.usuarioActual} · Rol: $rol")
        if (acceso.isNotEmpty()) Text("Permiso otorgado por el nodo: $acceso")
        if (!Sesion.puedeControlar && estado == "Conectado") Text("Modo de solo lectura: las órdenes requieren cuenta operadora y PIN de control.")
        if (estado == "Desconectado") {
            Button(onClick = {
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
        TarjetaLectura("Comida en el plato", comida, "g")
        TarjetaLectura("Agua en el estanque", agua, "%")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}", style = MaterialTheme.typography.titleMedium)
                Text("Ración simulada: ${gramos.toInt()} g")
                Slider(value = gramos, onValueChange = { gramos = it }, valueRange = 1f..60f, steps = 58, enabled = habilitado)
                Button(onClick = { Sesion.enviarComando("dispense", gramos.toInt().toString()) },
                    enabled = habilitado && comida != null && comida!! + gramos.toInt() <= 100, modifier = Modifier.fillMaxWidth()) {
                    Text("Dispensar ${gramos.toInt()} g")
                }
                Text("Capacidad del plato en esta simulación: 100 g.")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bomba: ${if (bomba) "ENCENDIDA" else "APAGADA"}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Sesion.enviarComando("pump", "ON") }, enabled = habilitado && !bomba && (agua ?: 0.0) >= 20) { Text("Encender") }
                    OutlinedButton(onClick = { Sesion.enviarComando("pump", "OFF") }, enabled = habilitado && bomba) { Text("Apagar") }
                }
                Text("Se apaga con agua baja, pérdida de enlace o tras 60 segundos.")
                OutlinedButton(onClick = { Sesion.enviarComando("refill_water", "ON") }, enabled = habilitado, modifier = Modifier.fillMaxWidth()) { Text("Simular relleno de agua al 95 %") }
            }
        }
        if (mensaje.isNotEmpty()) Text(mensaje, style = MaterialTheme.typography.titleSmall)
        Button(onClick = { Sesion.enviarComando("emergency_stop", "ON") }, enabled = Sesion.puedeControlar,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Detener actuadores") }
        OutlinedButton(onClick = onHistorial, modifier = Modifier.fillMaxWidth()) { Text("Ver historial de la simulación") }
        OutlinedButton(onClick = { credenciales = true }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar mi usuario y contraseña") }
        OutlinedButton(onClick = { Sesion.desconectar(); onDisconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar conexión") }
        TextButton(onClick = { Sesion.cerrarSesion() }) { Text("Cerrar sesión") }
    }
    if (credenciales) DialogoCambiarCredenciales(onDismiss = { credenciales = false }, onSuccess = { credenciales = false })
}

@Composable
private fun TarjetaLectura(titulo: String, valor: Double?, unidad: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(if (valor == null) "Sin lectura del nodo" else "%.1f %s".format(valor, unidad), style = MaterialTheme.typography.headlineSmall)
            if (valor != null) {
                LinearProgressIndicator(progress = { (valor / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(if (valor < 20) "Nivel bajo" else "Nivel suficiente en la simulación")
            }
        }
    }
}
