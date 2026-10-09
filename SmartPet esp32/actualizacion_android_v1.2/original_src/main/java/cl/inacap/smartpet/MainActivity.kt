package cl.inacap.smartpet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import cl.inacap.smartpet.ui.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Notificaciones.createChannel(this)

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var pantallaActual by rememberSaveable { mutableStateOf("LOGIN") }

                    when (pantallaActual) {
                        "LOGIN" -> PantallaLogin { pantallaActual = "CONEXION" }
                        "CONEXION" -> PantallaConexion(
                            onConnectedNodo = { pantallaActual = "NODO" },
                            onConnectedPanel = { pantallaActual = "PANEL" }
                        )
                        "NODO" -> PantallaNodo { pantallaActual = "CONEXION" }
                        "PANEL" -> PantallaPanel(
                            onDisconnect = { pantallaActual = "CONEXION" },
                            onHistorial = { pantallaActual = "HISTORIAL" }
                        )
                        "HISTORIAL" -> PantallaHistorial { pantallaActual = "PANEL" }
                    }
                }
            }
        }
    }
}
