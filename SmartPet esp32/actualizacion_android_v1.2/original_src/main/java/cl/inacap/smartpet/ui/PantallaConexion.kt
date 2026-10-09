package cl.inacap.smartpet.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.enlace.BluetoothEnlace
import cl.inacap.smartpet.enlace.WifiEnlace
import cl.inacap.smartpet.util.QrUtil
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun PantallaConexion(onConnectedNodo: () -> Unit, onConnectedPanel: () -> Unit) {
    val context = LocalContext.current
    var isNodo by remember { mutableStateOf(false) }
    var isWifi by remember { mutableStateOf(true) }
    var pin by remember { mutableStateOf(Sesion.ultimoPin) }
    var ip by remember { mutableStateOf(Sesion.ultimaIp) }

    var mostrarDialogoQrEstacion by remember { mutableStateOf(false) }
    var mostrarDialogoCredenciales by remember { mutableStateOf(false) }

    // Launcher de escaneo de QR (ZXing)
    val scanLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        if (result.contents != null) {
            val datos = QrUtil.parsearQr(result.contents)
            if (datos != null) {
                pin = datos.pin
                ip = datos.ip
                Sesion.ultimoPin = datos.pin
                Sesion.ultimaIp = datos.ip
                Toast.makeText(context, "✅ QR Escaneado: Estación ${datos.ip}", Toast.LENGTH_SHORT).show()
                // Conectar automáticamente al escanear
                val enlace = WifiEnlace(datos.pin, isNodo, datos.ip)
                Sesion.setEnlace(enlace)
                if (isNodo) onConnectedNodo() else onConnectedPanel()
            } else {
                Toast.makeText(context, "QR leído: ${result.contents}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher de permiso de cámara (solicita permiso solo una vez)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val options = ScanOptions().apply {
                setPrompt("Escanea el código QR de la estación SmartPet")
                setBeepEnabled(true)
                setOrientationLocked(false)
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            }
            scanLauncher.launch(options)
        } else {
            Toast.makeText(context, "Se necesita permiso de cámara para escanear el QR", Toast.LENGTH_SHORT).show()
        }
    }

    fun iniciarEscaneoQr() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val options = ScanOptions().apply {
                setPrompt("Escanea el código QR de la estación SmartPet")
                setBeepEnabled(true)
                setOrientationLocked(false)
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            }
            scanLauncher.launch(options)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val warmBg = Color(0xFFFFF9F2)
    val petOrange = Color(0xFFFF8A3D)
    val petBrown = Color(0xFF4A3428)
    val petBlue = Color(0xFF0288D1)

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
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = "Logo",
                        tint = petOrange,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vincular Estación",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = petBrown
                    )
                }
                Text(
                    text = "Configuración del Enlace Seguro",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- BOTÓN PRINCIPAL DE ESCANEAR CÓDIGO QR ---
                Button(
                    onClick = { iniciarEscaneoQr() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3428)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📷 Escanear Código QR de Estación",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tarjeta de Parámetros Manuales
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Sección: Modo del Dispositivo
                        Text(
                            text = "Modo de Operación",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = petBrown
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = !isNodo,
                                onClick = { isNodo = false },
                                label = { Text("Panel de Control", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFE0B2),
                                    selectedLabelColor = petBrown
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = isNodo,
                                onClick = { isNodo = true },
                                label = { Text("Nodo IoT", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFE0B2),
                                    selectedLabelColor = petBrown
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

                        // Sección: Medio Inalámbrico
                        Text(
                            text = "Medio de Comunicación",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = petBrown
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = isWifi,
                                onClick = { isWifi = true },
                                label = { Text("Wi-Fi TCP", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE1F5FE),
                                    selectedLabelColor = Color(0xFF0277BD)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !isWifi,
                                onClick = { isWifi = false },
                                label = { Text("Bluetooth", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE8EAF6),
                                    selectedLabelColor = Color(0xFF283593)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

                        // Input: PIN de Vinculación
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6) pin = it },
                            label = { Text("PIN de Cifrado (6 dígitos)") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = petOrange)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Input: IP (Solo si es Wi-Fi y Modo Panel)
                        if (isWifi && !isNodo) {
                            OutlinedTextField(
                                value = ip,
                                onValueChange = { ip = it },
                                label = { Text("IP de la Estación / PC Hub") },
                                leadingIcon = {
                                    Icon(Icons.Default.Router, contentDescription = null, tint = petBlue)
                                },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Atajos rápidos para USB vs Wi-Fi
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip(
                                    onClick = { ip = "127.0.0.1" },
                                    label = { Text("🔌 USB (127.0.0.1)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                SuggestionChip(
                                    onClick = { ip = "192.168.0.100" },
                                    label = { Text("📶 Wi-Fi (192.168.0.100)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Botón Conectar Manual
                        Button(
                            onClick = {
                                if (pin.length != 6) return@Button
                                Sesion.ultimoPin = pin
                                Sesion.ultimaIp = ip
                                val enlace = if (isWifi) {
                                    WifiEnlace(pin, isNodo, ip)
                                } else {
                                    BluetoothEnlace(pin, isNodo, null, android.bluetooth.BluetoothAdapter.getDefaultAdapter())
                                }
                                Sesion.setEnlace(enlace)
                                if (isNodo) onConnectedNodo() else onConnectedPanel()
                            },
                            enabled = pin.length == 6 && (!isWifi || isNodo || ip.isNotEmpty()),
                            colors = ButtonDefaults.buttonColors(containerColor = petOrange),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Establecer Conexión",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fila de acciones secundarias: Ver QR y Cambiar Usuario/Contraseña
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { mostrarDialogoQrEstacion = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver QR Único", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { mostrarDialogoCredenciales = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = petOrange),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp), tint = petOrange)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar Usuario", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Margen generoso al fondo para que nada quede al ras
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    // Modal para mostrar el Código QR Único de la Estación
    if (mostrarDialogoQrEstacion) {
        val qrBitmap = remember(pin, ip) {
            try {
                QrUtil.generarQrBitmap("SMARTPET:$pin:$ip", 512)
            } catch (e: Exception) {
                null
            }
        }

        AlertDialog(
            onDismissRequest = { mostrarDialogoQrEstacion = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = petOrange, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Código QR de Vinculación", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = petBrown)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Escanea este código con otro celular para vincularte instantáneamente a esta estación SmartPet:",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )

                    if (qrBitmap != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(4.dp),
                            modifier = Modifier.size(230.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Código QR de la Estación",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Text(
                        text = "IP: $ip  ·  PIN: $pin",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = petBrown
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { mostrarDialogoQrEstacion = false },
                    colors = ButtonDefaults.buttonColors(containerColor = petOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal para Cambiar Usuario y Contraseña (Uso Único / Configuración Inicial)
    if (mostrarDialogoCredenciales) {
        DialogoCambiarCredenciales(
            onDismiss = { mostrarDialogoCredenciales = false },
            onSuccess = { nuevoUser ->
                mostrarDialogoCredenciales = false
            }
        )
    }
}
