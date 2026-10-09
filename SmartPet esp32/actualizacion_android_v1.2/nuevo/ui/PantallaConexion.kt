package cl.inacap.smartpet.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.enlace.*
import cl.inacap.smartpet.util.QrUtil
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun PantallaConexion(onConnectedNodo: () -> Unit, onConnectedPanel: () -> Unit) {
    val context = LocalContext.current
    var nodo by rememberSaveable { mutableStateOf(false) }
    var wifi by rememberSaveable { mutableStateOf(Sesion.medioWifi) }
    var pin by remember { mutableStateOf(Sesion.ultimoPin) }
    var ip by rememberSaveable { mutableStateOf(Sesion.ultimaIp) }
    var error by remember { mutableStateOf("") }
    var dispositivos by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var seleccionado by remember { mutableStateOf<BluetoothDevice?>(null) }
    var credenciales by remember { mutableStateOf(false) }
    val rol by Sesion.rol.collectAsState()
    LaunchedEffect(rol) { if (!Sesion.esOperador) nodo = false }
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val permisoRed = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        error = if (it) "Permiso concedido. Pulsa de nuevo el botón de conexión." else "Se necesita el permiso de red local para comunicar los celulares por Wi-Fi"
    }

    fun actualizarBluetooth() {
        try {
            if (adapter == null || !adapter.isEnabled) { error = "Activa Bluetooth en los ajustes del teléfono"; return }
            dispositivos = adapter.bondedDevices.toList()
            if (dispositivos.isEmpty()) error = "Empareja los celulares desde los ajustes de Bluetooth y actualiza la lista"
            else error = ""
        } catch (_: SecurityException) { error = "Concede el permiso de dispositivos cercanos" }
    }
    val permisoBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) actualizarBluetooth() else error = "Permiso de Bluetooth denegado"
    }
    fun prepararBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            permisoBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else actualizarBluetooth()
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val datos = QrUtil.parsearQr(result.contents)
            if (datos == null) error = "El QR no contiene una estación válida"
            else { pin = datos.pin; ip = datos.ip; nodo = false; wifi = true; error = "QR leído. Pulsa Establecer Conexión." }
        }
    }
    fun escanear() { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Escanea el QR mostrado por el nodo").setOrientationLocked(false)) }
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) escanear() else error = "Puedes escribir la IP y el PIN manualmente"
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Vincular SmartPet", style = MaterialTheme.typography.headlineMedium)
        Text("Usuario: ${Sesion.usuarioActual} · ${Sesion.rol.collectAsState().value}")
        Text("Modo de operación", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !nodo, onClick = { nodo = false; pin = Sesion.ultimoPin }, label = { Text("Panel de Control") })
            FilterChip(selected = nodo, enabled = Sesion.esOperador, onClick = { nodo = true }, label = { Text("Nodo IoT") })
        }
        if (!Sesion.esOperador) Text("La cuenta observadora puede monitorear; el operador inicia el nodo.")
        Text("Medio de comunicación", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = wifi, onClick = { wifi = true }, label = { Text("Wi-Fi TCP") })
            FilterChip(selected = !wifi, onClick = { wifi = false; prepararBluetooth() }, label = { Text("Bluetooth") })
        }
        if (nodo && Sesion.esOperador) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (wifi) Text("IP de este celular: ${WifiEnlace.getLocalIpAddress().ifEmpty { "Conéctate a una red Wi-Fi" }}")
                    Text("PIN de control: ${Sesion.pinNodo()}")
                    Text("PIN de solo lectura: ${Sesion.pinNodo(true)}")
                    Text("Comparte el PIN de control solo con el operador. Los dos celulares deben usar la misma red Wi-Fi.")
                }
            }
        } else {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("PIN mostrado por el nodo") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            if (wifi) {
                OutlinedTextField(ip, { ip = it.trim() }, label = { Text("IP del celular nodo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Busca la IP en el nodo. Una dirección de ejemplo no conecta los celulares.")
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) escanear()
                    else permisoCamara.launch(Manifest.permission.CAMERA)
                }) { Text("Escanear QR del nodo") }
            }
        }
        if (!wifi) {
            Text("Empareja los teléfonos previamente desde los ajustes de Bluetooth.")
            OutlinedButton(onClick = { prepararBluetooth() }) { Text("Actualizar dispositivos emparejados") }
            if (!nodo) dispositivos.forEach { device ->
                val nombre = try { device.name ?: device.address } catch (_: SecurityException) { "Dispositivo" }
                FilterChip(selected = seleccionado == device, onClick = { seleccionado = device }, label = { Text(nombre) })
            }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            error = ""
            if (nodo && !Sesion.esOperador) { nodo = false; error = "Se requiere cuenta operadora para iniciar el nodo"; return@Button }
            if (wifi && Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                permisoRed.launch("android.permission.ACCESS_LOCAL_NETWORK"); return@Button
            }
            val codigo = if (nodo) Sesion.pinNodo() else pin
            if (!codigo.matches(Regex("[0-9]{6}"))) { error = "Introduce un PIN de 6 dígitos"; return@Button }
            if (wifi && !nodo && !QrUtil.ipValida(ip)) { error = "Escribe la IP Wi-Fi válida del nodo"; return@Button }
            if (wifi && nodo && WifiEnlace.getLocalIpAddress().isEmpty()) { error = "Conecta este celular al Wi-Fi"; return@Button }
            if (!wifi) {
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    prepararBluetooth(); return@Button
                }
                try { if (adapter == null || !adapter.isEnabled) { error = "Activa Bluetooth"; return@Button } }
                catch (_: SecurityException) { prepararBluetooth(); return@Button }
                if (!nodo && seleccionado == null) { error = "Selecciona el celular nodo"; return@Button }
            }
            if (!nodo) Sesion.ultimoPin = codigo
            Sesion.ultimaIp = ip; Sesion.medioWifi = wifi
            Sesion.direccionBluetooth = try { seleccionado?.address ?: "" } catch (_: SecurityException) { "" }
            Sesion.desconectar()
            val enlace = if (wifi) WifiEnlace(codigo, nodo, ip, if (nodo) Sesion.pinNodo(true) else null, Sesion.rol.value)
                else BluetoothEnlace(codigo, nodo, seleccionado, adapter, if (nodo) Sesion.pinNodo(true) else null, Sesion.rol.value)
            Sesion.setEnlace(enlace, nodo)
            if (nodo) onConnectedNodo() else onConnectedPanel()
        }, modifier = Modifier.fillMaxWidth()) { Text(if (nodo) "Iniciar Nodo Simulador" else "Establecer Conexión") }
        OutlinedButton(onClick = { credenciales = true }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar mi usuario y contraseña") }
        TextButton(onClick = { Sesion.cerrarSesion() }) { Text("Cerrar sesión") }
    }
    if (credenciales) DialogoCambiarCredenciales(onDismiss = { credenciales = false }, onSuccess = { credenciales = false })
}
