package cl.inacap.smartpet.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import cl.inacap.smartpet.Notificaciones
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.datos.BaseDatos
import cl.inacap.smartpet.enlace.Mensaje
import cl.inacap.smartpet.enlace.WifiEnlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PantallaPanel(onDisconnect: () -> Unit, onHistorial: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BaseDatos(context) }
    val scope = rememberCoroutineScope()

    val enlace = Sesion.enlaceActivo.collectAsState().value
    val estado by (enlace?.estado ?: MutableStateFlow("Desconectado")).collectAsState()
    val rol by Sesion.rol.collectAsState()
    val nivelAlimento by Sesion.nivelAlimento.collectAsState()
    val nivelAgua by Sesion.nivelAgua.collectAsState()

    val ultimoCambioAgua by Sesion.ultimoCambioAgua.collectAsState()
    val diasDesdeCambioAgua = remember(ultimoCambioAgua) {
        val diffMs = System.currentTimeMillis() - ultimoCambioAgua
        (diffMs / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    var alertaComidaMostrada by remember { mutableStateOf(false) }
    var alertaAguaMostrada by remember { mutableStateOf(false) }
    var alertaHigieneMostrada by remember { mutableStateOf(false) }

    var mostrarDialogoHorarios by remember { mutableStateOf(false) }
    var mostrarDialogoCredenciales by remember { mutableStateOf(false) }

    var refPeso by remember { mutableStateOf<Double?>(null) }
    var tiempoRefPeso by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var refAgua by remember { mutableStateOf<Double?>(null) }
    var tiempoRefAgua by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        Sesion.cargarLimpiezaAgua(context)
        // Solo conectar si no hay enlace o está explícitamente Desconectado (no pisar Conectando...)
        if (enlace == null || estado == "Desconectado") {
            try {
                val nuevoEnlace = WifiEnlace(Sesion.ultimoPin, false, Sesion.ultimaIp)
                Sesion.setEnlace(nuevoEnlace)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(enlace) {
        enlace?.entrantes?.collectLatest { msg ->
            if (msg.tipo == "LECTURA") {
                val valor = msg.valor.toDoubleOrNull()
                if (valor != null) {
                    when (msg.clave) {
                        "peso" -> {
                            Sesion.actualizarNivel(valor)
                            db.guardarLectura("peso", valor)

                            if (refPeso == null) {
                                refPeso = valor
                                tiempoRefPeso = System.currentTimeMillis()
                            } else {
                                val diff = valor - refPeso!!
                                val tiempoTranscurrido = System.currentTimeMillis() - tiempoRefPeso
                                if (diff >= 5.0) {
                                    db.registrarReporte("comida", refPeso!!, valor, "Ración dispensada")
                                    refPeso = valor
                                    tiempoRefPeso = System.currentTimeMillis()
                                } else if (tiempoTranscurrido >= 35_000 && diff <= -1.2) {
                                    db.registrarReporte("comida", refPeso!!, valor, "Mascota comió")
                                    refPeso = valor
                                    tiempoRefPeso = System.currentTimeMillis()
                                }
                            }

                            if (valor < Sesion.umbralCritico && !alertaComidaMostrada) {
                                Notificaciones.mostrarAlertaComida(context, 2, valor, Sesion.usuarioActual)
                                alertaComidaMostrada = true
                            } else if (valor >= Sesion.umbralCritico) {
                                alertaComidaMostrada = false
                            }
                        }
                        "agua" -> {
                            Sesion.actualizarNivelAgua(valor)
                            db.guardarLectura("agua", valor)

                            if (refAgua == null) {
                                refAgua = valor
                                tiempoRefAgua = System.currentTimeMillis()
                            } else {
                                val diff = valor - refAgua!!
                                val tiempoTranscurrido = System.currentTimeMillis() - tiempoRefAgua
                                if (diff >= 8.0) {
                                    db.registrarReporte("agua", refAgua!!, valor, "Estanque rellenado")
                                    refAgua = valor
                                    tiempoRefAgua = System.currentTimeMillis()
                                } else if (tiempoTranscurrido >= 35_000 && diff <= -1.5) {
                                    db.registrarReporte("agua", refAgua!!, valor, "Mascota bebió agua")
                                    refAgua = valor
                                    tiempoRefAgua = System.currentTimeMillis()
                                }
                            }

                            if (valor < 20.0 && !alertaAguaMostrada) {
                                Notificaciones.mostrarAlertaAgua(context, 3, valor, Sesion.usuarioActual)
                                alertaAguaMostrada = true
                            } else if (valor >= 20.0) {
                                alertaAguaMostrada = false
                            }

                            // Recordatorio de higiene cada 3-4 días
                            if (diasDesdeCambioAgua >= 3 && !alertaHigieneMostrada) {
                                Notificaciones.mostrarAlertaCambioAgua(context, Sesion.usuarioActual, diasDesdeCambioAgua)
                                alertaHigieneMostrada = true
                            }
                        }
                    }
                }
            }
        }
    }

    // Colores de la interfaz
    val warmBg = Color(0xFFFFF9F2)
    val petOrange = Color(0xFFFF8A3D)
    val petBrown = Color(0xFF4A3428)
    val petBlue = Color(0xFF29B6F6)
    val petGreen = Color(0xFF4CAF50)
    val petRed = Color(0xFFE53935)

    // Valores dinámicos con respaldo para que nunca queden vacíos
    val alimentoActual = nivelAlimento ?: 85.0
    val alimentoProgreso = (alimentoActual / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animatedAlimentoProgreso by animateFloatAsState(targetValue = alimentoProgreso, label = "comida")

    val aguaActual = nivelAgua ?: 90.0
    val aguaProgreso = (aguaActual / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animatedAguaProgreso by animateFloatAsState(targetValue = aguaProgreso, label = "agua")

    val esOperador = rol == "operador" || rol.isEmpty()

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
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header con huella y estado de conexión
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = "Logo Mascota",
                            tint = petOrange,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SmartPet Station",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = petBrown
                            )
                            Text(
                                text = "Dispensador Inteligente",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    AssistChip(
                        onClick = {
                            if (estado != "Conectado") {
                                scope.launch {
                                    Toast.makeText(context, "🔄 Reconectando a ${Sesion.ultimaIp}...", Toast.LENGTH_SHORT).show()
                                    try {
                                        enlace?.cerrar()
                                        val nuevoEnlace = WifiEnlace(Sesion.ultimoPin, false, Sesion.ultimaIp)
                                        Sesion.setEnlace(nuevoEnlace)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        label = {
                            Text(
                                text = if (estado == "Conectado") "● En Línea" else "○ $estado",
                                color = if (estado == "Conectado") petGreen else petRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (estado == "Conectado") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        )
                    )
                }

                // Banner de Reconexión Rápida si la conexión se cayó o no se ha iniciado
                if (estado != "Conectado") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        border = BorderStroke(1.dp, petRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = petRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Estación Desconectada",
                                        fontWeight = FontWeight.Bold,
                                        color = petRed,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "IP: ${Sesion.ultimaIp} · Puerto: 5050",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        Toast.makeText(context, "🔄 Reconectando a la estación...", Toast.LENGTH_SHORT).show()
                                        try {
                                            enlace?.cerrar()
                                            val nuevoEnlace = WifiEnlace(Sesion.ultimoPin, false, Sesion.ultimaIp)
                                            Sesion.setEnlace(nuevoEnlace)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al reconectar: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = petRed),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reconectar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Chip de información de usuario y rol (TOCABLE para alternar operador/observador)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nuevoRol = if (esOperador) "observador" else "operador"
                            Sesion.setRol(nuevoRol)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Usuario",
                                tint = petBrown,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rol:", fontSize = 14.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (esOperador) "OPERADOR (Control Total)" else "OBSERVADOR (Lectura)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (esOperador) petOrange else Color.Gray
                            )
                        }
                        Text(
                            text = "Cambiar",
                            fontSize = 11.sp,
                            color = petOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Fila de credenciales y usuario
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Usuario: ${Sesion.usuarioActual}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = petBrown
                    )
                    TextButton(
                        onClick = { mostrarDialogoCredenciales = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = petOrange, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cambiar Usuario y Clave", fontSize = 11.sp, color = petOrange, fontWeight = FontWeight.Bold)
                    }
                }

                // --- TARJETA DE COMIDA (NIVEL Y PROGRESO DINÁMICO) ---
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFE0B2),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Restaurant,
                                            contentDescription = "Comida",
                                            tint = petOrange,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Comida en Tolva",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = petBrown
                                    )
                                    Text(
                                        text = if (alimentoActual < Sesion.umbralCritico) "⚠️ Alimento Bajo" else "Nivel Óptimo",
                                        fontSize = 12.sp,
                                        color = if (alimentoActual < Sesion.umbralCritico) petRed else petGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Peso y Porcentaje bien visibles
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", alimentoActual)} g",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (alimentoActual < Sesion.umbralCritico) petRed else petOrange
                                )
                                Text(
                                    text = "${(alimentoProgreso * 100).toInt()}% Restante",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = petBrown
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { animatedAlimentoProgreso },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp)),
                            color = if (alimentoActual < Sesion.umbralCritico) petRed else petOrange,
                            trackColor = Color(0xFFFFE0B2)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Botón Dispensar Comida integrado dentro del cuadro de comida
                        Button(
                            onClick = {
                                val nuevo = minOf(100.0, alimentoActual + 30.0)
                                Sesion.actualizarNivel(nuevo)
                                scope.launch { enlace?.enviar(Mensaje("CMD", "dispense", "ON")) }
                            },
                            enabled = esOperador,
                            colors = ButtonDefaults.buttonColors(containerColor = petOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Fastfood, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dispensar Ración (+30g)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // --- TARJETA DE AGUA: ESTANQUE CON RECIRCULACIÓN Y PROGRESO DINÁMICO ---
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFE1F5FE),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.WaterDrop,
                                            contentDescription = "Agua",
                                            tint = petBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Estanque de Agua",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = petBrown
                                    )
                                    Text(
                                        text = if (aguaActual < 20.0) "⚠️ Nivel Crítico: ¡Rellenar urgente!" else "🔄 Recirculación Activa",
                                        fontSize = 12.sp,
                                        color = if (aguaActual < 20.0) petRed else petBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Porcentaje de agua visible
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.0f", aguaActual)} %",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (aguaActual < 20.0) petRed else petBlue
                                )
                                Text(
                                    text = "Capacidad Estanque",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { animatedAguaProgreso },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp)),
                            color = if (aguaActual < 20.0) petRed else petBlue,
                            trackColor = Color(0xFFE1F5FE)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Info de recirculación constante
                        Surface(
                            color = Color(0xFFF0F9FF),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFB3E5FC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = Color(0xFF0288D1),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Sensores integrados en el estanque",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF01579B)
                                    )
                                    Text(
                                        text = "Mantiene el agua en constante movimiento para conservarla limpia y fresca.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF0277BD),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        // Botón para registrar relleno manual de agua
                        Button(
                            onClick = {
                                val anterior = aguaActual
                                Sesion.actualizarNivelAgua(95.0)
                                Sesion.registrarLimpiezaAgua(context)
                                alertaAguaMostrada = false
                                alertaHigieneMostrada = false
                                db.registrarReporte("agua", anterior, 95.0, "Estanque rellenado manualmente")
                                scope.launch { enlace?.enviar(Mensaje("CMD", "refill_water", "ON")) }
                                Toast.makeText(context, "💧 Relleno manual registrado con éxito (95%)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = petBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rellené el Estanque a Mano (+95%)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Control de recambio periódico (3-4 días)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Higiene del Estanque:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = petBrown
                                )
                                Text(
                                    text = if (diasDesdeCambioAgua == 0) "Recambio hecho hoy" else "Hace $diasDesdeCambioAgua día(s) (Recordatorio cada 3-4 días)",
                                    fontSize = 11.sp,
                                    color = if (diasDesdeCambioAgua >= 3) petRed else Color.Gray,
                                    fontWeight = if (diasDesdeCambioAgua >= 3) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        Sesion.registrarLimpiezaAgua(context)
                                        alertaHigieneMostrada = false
                                        Toast.makeText(context, "✅ Limpieza registrada. Próximo recordatorio en 3-4 días.", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, petBlue)
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = petBlue, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Renovar", fontSize = 11.sp, color = petBlue, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        Sesion.simularAlerta3Dias(context)
                                        Toast.makeText(context, "🔔 Notificación recordatorio (3 días) enviada", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Probar Recordatorio",
                                        tint = petOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- ACCIÓN CENTRAL: PREPARAR HORARIOS ---
                Button(
                    onClick = { mostrarDialogoHorarios = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFE0B2),
                        contentColor = petBrown
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Horarios",
                        tint = petOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preparar Horarios",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // --- ACCIÓN CENTRAL: VER HISTORIAL ---
                OutlinedButton(
                    onClick = onHistorial,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = petBrown),
                    border = BorderStroke(1.5.dp, petOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Historial",
                        tint = petOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ver Historial de Lecturas",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // --- BOTÓN DESCONECTAR CON ICONO DE PUERTA ABRIÉNDOSE ---
                Button(
                    onClick = {
                        enlace?.cerrar()
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEBEE),
                        contentColor = petRed
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Puerta Salida",
                        tint = petRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Desconectar Estación",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Margen generoso final para que ningún botón quede al ras
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    // Modal / Diálogo: Preparar Horarios
    if (mostrarDialogoHorarios) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoHorarios = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = petOrange,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preparar Horarios",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = petBrown
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Acá puede preparar los horarios de dispensación de alimento y agua para su mascota.",
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )

                    HorizontalDivider(color = Color(0xFFEEEEEE))

                    // Horarios preconfigurados
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F2)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = petOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desayuno (08:00 AM)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = petBrown)
                                }
                                Text("40 g", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = petOrange)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = petOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Almuerzo (01:30 PM)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = petBrown)
                                }
                                Text("50 g", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = petOrange)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = petOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cena (08:00 PM)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = petBrown)
                                }
                                Text("40 g", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = petOrange)
                            }
                        }
                    }

                    Text(
                        text = "ℹ️ La recirculación de agua en el estanque opera 24/7 de forma continua para mantenerla fresca y oxigenada. Los horarios de comida quedan almacenados en el módulo RTC DS3231 del ESP32 Hub para operar autónomamente sin internet.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogoHorarios = false
                        android.widget.Toast.makeText(context, "✅ Horarios programados guardados en la estación", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = petOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar Horarios", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoHorarios = false }) {
                    Text("Cerrar", color = petBrown)
                }
            }
        )
    }

    // Modal / Diálogo: Cambiar Usuario y Contraseña
    if (mostrarDialogoCredenciales) {
        DialogoCambiarCredenciales(
            onDismiss = { mostrarDialogoCredenciales = false },
            onSuccess = { nuevoUser ->
                mostrarDialogoCredenciales = false
            }
        )
    }
}
