package cl.inacap.smartpet.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Notificaciones
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.enlace.Mensaje
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@Composable
fun PantallaNodo(onDisconnect: () -> Unit) {
    val context = LocalContext.current
    var peso by rememberSaveable { mutableStateOf(50f) }
    var agua by rememberSaveable { mutableStateOf(90f) }
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

    LaunchedEffect(enlace) {
        while (true) {
            if (estado == "Conectado") {
                enlace?.enviar(Mensaje("LECTURA", "peso", String.format(Locale.US, "%.1f", peso)))
                enlace?.enviar(Mensaje("LECTURA", "agua", String.format(Locale.US, "%.1f", agua)))
            }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Nodo Simulador", style = MaterialTheme.typography.headlineMedium)
        val estadoVisible = if (estado == "Conectando...") "Esperando conexión del panel..." else estado
        Text("Estado: $estadoVisible")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Mueve los controles para simular el consumo de comida y agua.")

        Spacer(modifier = Modifier.height(24.dp))
        Text("Comida en el plato: ${String.format(Locale.US, "%.1f", peso)} g")
        Slider(
            value = peso,
            onValueChange = { peso = it },
            valueRange = 0f..100f
        )
        Text("0 g: plato vacío · 100 g: plato lleno", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(24.dp))
        Text("Nivel de agua: ${String.format(Locale.US, "%.1f", agua)} %")
        Slider(
            value = agua,
            onValueChange = { agua = it },
            valueRange = 0f..100f
        )
        Text("0 %: estanque vacío · 100 %: estanque lleno", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Las lecturas simuladas se envían al panel cada 2 segundos.")

        Spacer(modifier = Modifier.height(24.dp))
        Text("Actuadores:")
        Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}")
        Text("Bomba de agua: ${if (bomba) "ENCENDIDA" else "APAGADA"}")

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {
            enlace?.cerrar()
            onDisconnect()
        }) {
            Text("Desconectar")
        }
    }
}
