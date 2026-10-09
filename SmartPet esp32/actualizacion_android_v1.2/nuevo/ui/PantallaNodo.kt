package cl.inacap.smartpet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.enlace.WifiEnlace
import cl.inacap.smartpet.util.QrUtil

@Composable
fun PantallaNodo(onDisconnect: () -> Unit) {
    val enlace by Sesion.enlaceActivo.collectAsState()
    val actual = enlace ?: return
    val estado by actual.estado.collectAsState()
    val detalle by actual.detalle.collectAsState()
    val acceso by actual.acceso.collectAsState()
    val peso by Sesion.nivelAlimento.collectAsState()
    val agua by Sesion.nivelAgua.collectAsState()
    val dispensador by Sesion.dispensadorActivo.collectAsState()
    val bomba by Sesion.bombaActiva.collectAsState()
    var qrLectura by remember { mutableStateOf<Boolean?>(null) }
    val ip = WifiEnlace.getLocalIpAddress()
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Nodo Simulador", style = MaterialTheme.typography.headlineMedium)
        Text("Estado: $estado")
        if (detalle.isNotEmpty()) Text(detalle)
        if (acceso.isNotEmpty()) Text("Panel vinculado: $acceso")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Sesion.medioWifi) Text("IP de este celular: $ip")
                Text("PIN de control: ${Sesion.pinNodo()}")
                Text("PIN de solo lectura: ${Sesion.pinNodo(true)}")
                if (Sesion.medioWifi) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { qrLectura = false }) { Text("QR control") }
                    OutlinedButton(onClick = { qrLectura = true }) { Text("QR lectura") }
                }
            }
        }
        Text("Mueve los controles para simular el consumo. El panel recibe lecturas cada 2 segundos.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Comida en el plato: ${"%.1f".format(peso ?: 0.0)} g", style = MaterialTheme.typography.titleMedium)
                Slider(value = (peso ?: 0.0).toFloat(), onValueChange = { Sesion.ajustarPeso(it) },
                    enabled = !dispensador, valueRange = 0f..100f)
                Text("0 g: vacío · 100 g: capacidad de simulación")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Agua: ${"%.1f".format(agua ?: 0.0)} %", style = MaterialTheme.typography.titleMedium)
                Slider(value = (agua ?: 0.0).toFloat(), onValueChange = { Sesion.ajustarAgua(it) }, valueRange = 0f..100f)
                Text("La bomba se apaga con menos de 20 % de agua.")
            }
        }
        Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}")
        Text("Bomba: ${if (bomba) "ENCENDIDA" else "APAGADA"}")
        Text("La ración se añade al plato al terminar el dispensado simulado. Ante pérdida de enlace se detienen los actuadores.")
        Button(onClick = { Sesion.desconectar(); onDisconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Detener nodo y desconectar") }
    }
    qrLectura?.let { lectura ->
        val bitmap = remember(lectura, ip) { QrUtil.generarQrBitmap("SMARTPET:${Sesion.pinNodo(lectura)}:$ip") }
        AlertDialog(onDismissRequest = { qrLectura = null }, title = { Text(if (lectura) "Acceso de solo lectura" else "Acceso de control") },
            text = { Column { Image(bitmap.asImageBitmap(), contentDescription = "QR de vinculación", modifier = Modifier.fillMaxWidth().aspectRatio(1f)); Text("IP: $ip") } },
            confirmButton = { TextButton(onClick = { qrLectura = null }) { Text("Cerrar") } })
    }
}
