package cl.inacap.smartpet

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import cl.inacap.smartpet.ui.*

class MainActivity : ComponentActivity() {
    private val permisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sesion.inicializar(applicationContext)
        Notificaciones.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33) permisos.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFFF8A3D), secondary = Color(0xFF0288D1),
                background = PetCream, surface = PetCream, surfaceVariant = Color(0xFFFFE0B2),
                onPrimary = Color.White, onBackground = PetBrown, onSurface = PetBrown, onSurfaceVariant = PetBrown),
                shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp))) {
                val autenticado by Sesion.autenticado.collectAsState()
                val enlace by Sesion.enlaceActivo.collectAsState()
                var pantalla by rememberSaveable { mutableStateOf("LOGIN") }
                LaunchedEffect(autenticado) { if (!autenticado) pantalla = "LOGIN" }
                Surface {
                    when {
                        !autenticado -> PantallaLogin { pantalla = "CONEXION" }
                        pantalla == "NODO" && enlace != null -> PantallaNodo { pantalla = "CONEXION" }
                        pantalla == "PANEL" && enlace != null -> PantallaPanel({ pantalla = "CONEXION" }, { pantalla = "HISTORIAL" })
                        pantalla == "HISTORIAL" -> PantallaHistorial { pantalla = if (enlace != null) "PANEL" else "CONEXION" }
                        else -> PantallaConexion({ pantalla = "NODO" }, { pantalla = "PANEL" })
                    }
                }
            }
        }
    }
}
