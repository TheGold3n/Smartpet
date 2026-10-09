package cl.inacap.smartpet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PetOrange = Color(0xFFFF8A3D)
val PetBrown = Color(0xFF4A3428)
val PetBlue = Color(0xFF29B6F6)
val PetCream = Color(0xFFFFF9F2)

@Composable
fun PetScreen(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun PetCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun PetBrand(title: String, subtitle: String, centered: Boolean = false) {
    if (centered) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = Color(0xFFFFE0B2), shape = RoundedCornerShape(24.dp)) {
                Icon(Icons.Default.Pets, contentDescription = null, tint = PetOrange, modifier = Modifier.padding(16.dp).size(44.dp))
            }
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = PetBrown, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Pets, contentDescription = null, tint = PetOrange, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = PetBrown)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    }
}

@Composable
fun PetConnectionState(estado: String, detalle: String) {
    val conectado = estado == "Conectado"
    val desconectado = estado == "Desconectado"
    val color = if (conectado) Color(0xFF388E3C) else if (desconectado) Color(0xFFE53935) else Color(0xFFB26A00)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f), border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (conectado) Icons.Default.Wifi else Icons.Default.Sensors, contentDescription = null, tint = color)
            Column(Modifier.weight(1f)) {
                Text(estado, color = color, fontWeight = FontWeight.Bold)
                if (detalle.isNotEmpty()) Text(detalle, style = MaterialTheme.typography.bodySmall, color = PetBrown)
            }
        }
    }
}

@Composable
fun PetReading(titulo: String, valor: Double?, agua: Boolean) {
    val color = if (agua) PetBlue else PetOrange
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = if (agua) Color(0xFFE1F5FE) else Color(0xFFFFE0B2)) {
            Icon(if (agua) Icons.Default.WaterDrop else Icons.Default.Restaurant, contentDescription = null,
                tint = color, modifier = Modifier.padding(12.dp).size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PetBrown)
            Text(if (valor == null) "Sin lectura del nodo" else if (valor < 20) "Nivel bajo" else "Nivel óptimo",
                color = if (valor != null && valor < 20) Color(0xFFE53935) else Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (valor != null) {
        Text("%.1f %s".format(valor, if (agua) "%" else "g"), color = color, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        LinearProgressIndicator(progress = { (valor / 100).toFloat().coerceIn(0f, 1f) },
            color = color, trackColor = color.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth().height(12.dp))
    }
}
