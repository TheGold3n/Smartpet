package cl.inacap.smartpet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Notificaciones
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.enlace.Mensaje
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PantallaNodo(onDisconnect: () -> Unit) {
    val context = LocalContext.current
    var peso by remember { mutableStateOf(50f) }
    val dispensador by Sesion.dispensadorActivo.collectAsState()
    val bomba by Sesion.bombaActiva.collectAsState()
    val enlace = Sesion.enlaceActivo.collectAsState().value
    val estado by (enlace?.estado ?: MutableStateFlow("Desconectado")).collectAsState()

    LaunchedEffect(estado) {
        if (estado == "Desconectado") {
            Sesion.setDispensadorActivo(false)
            Sesion.setBombaActiva(false)
            Notificaciones.mostrar(context, 1, "Alerta", "Enlace perdido. Estado Seguro activado.")
        }
    }

    LaunchedEffect(enlace) {
        enlace?.entrantes?.collectLatest { msg ->
            if (msg.tipo == "CMD") {
                when (msg.clave) {
                    "dispense" -> Sesion.setDispensadorActivo(msg.valor == "ON")
                    "pump" -> Sesion.setBombaActiva(msg.valor == "ON")
                }
                enlace.enviar(Mensaje("ACK", msg.clave, msg.valor))
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (estado == "Conectado") {
                val valorStr = String.format(java.util.Locale.US, "%.1f", peso)
                enlace?.enviar(Mensaje("LECTURA", "peso", valorStr))
            }
            delay(2000)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Nodo Simulador", style = MaterialTheme.typography.headlineMedium)
        Text("Estado: $estado")
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Peso/Nivel de alimento: ${"%.1f".format(peso)} g")
        Slider(
            value = peso,
            onValueChange = { peso = it },
            valueRange = 0f..100f
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Actuadores:")
        Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}")
        Text("Bomba de agua: ${if (bomba) "ENCENDIDA" else "APAGADA"}")
        
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { 
            enlace?.cerrar()
            onDisconnect()
        }) {
            Text("Desconectar")
        }
    }
}
