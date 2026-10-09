package cl.inacap.smartpet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.smartpet.datos.BaseDatos
import cl.inacap.smartpet.datos.ReporteConsumo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PantallaHistorial(onBack: () -> Unit) {
    val context = LocalContext.current
    var reportes by remember { mutableStateOf<List<ReporteConsumo>>(emptyList()) }
    LaunchedEffect(context) {
        while (isActive) {
            reportes = withContext(Dispatchers.IO) { BaseDatos(context).use { it.reportes(50) } }
            delay(2000)
        }
    }
    val dateFormat = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())

    val warmBg = Color(0xFFFFF9F2)
    val petOrange = Color(0xFFFF8A3D)
    val petBrown = Color(0xFF4A3428)
    val petBlue = Color(0xFF29B6F6)
    val petGreen = Color(0xFF4CAF50)

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
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 440.dp)
            ) {
                // Encabezado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = petBrown
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = null,
                        tint = petOrange,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Historial de Simulación",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = petBrown
                        )
                        Text(
                            text = "Lecturas recibidas del nodo Android",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (reportes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no hay reportes de consumo registrados",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(reportes) { rep ->
                            val isAgua = rep.tipo == "agua"
                            val diff = rep.valorNuevo - rep.valorAnterior
                            val esRecarga = diff > 0
                            val unidad = if (isAgua) "%" else "g"

                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Ícono con fondo suave
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isAgua) Color(0xFFE1F5FE) else Color(0xFFFFE0B2),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isAgua) {
                                                    Icons.Default.WaterDrop
                                                } else if (esRecarga) {
                                                    Icons.Default.Fastfood
                                                } else {
                                                    Icons.Default.Restaurant
                                                },
                                                contentDescription = null,
                                                tint = if (isAgua) petBlue else petOrange,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    // Contenido del reporte
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isAgua) "Nivel de Agua" else "Nivel de Comida",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = petBrown
                                            )
                                            Text(
                                                text = dateFormat.format(Date(rep.momento)),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Frase exacta solicitada: "pasó de X a Y"
                                        Text(
                                            text = if (isAgua) {
                                                "Pasó de ${String.format(Locale.US, "%.1f", rep.valorAnterior)}% a ${String.format(Locale.US, "%.1f", rep.valorNuevo)}%"
                                            } else {
                                                "Pasó de ${String.format(Locale.US, "%.1f", rep.valorAnterior)}g a ${String.format(Locale.US, "%.1f", rep.valorNuevo)}g"
                                            },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = petBrown
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Detalle y variación (+30g o -3.3g)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (esRecarga) {
                                                    "+${String.format(Locale.US, "%.1f", diff)} $unidad"
                                                } else {
                                                    "${String.format(Locale.US, "%.1f", diff)} $unidad"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (esRecarga) petGreen else petOrange
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• ${rep.detalle}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botón Volver con margen seguro
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = petOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Volver al Panel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                // Margen generoso final
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
