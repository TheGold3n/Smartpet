package cl.inacap.smartpet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import cl.inacap.smartpet.datos.BaseDatos
import cl.inacap.smartpet.enlace.Mensaje
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISPENSE_40 = "cl.inacap.smartpet.ACTION_DISPENSE_40"
        const val ACTION_FILL_SAFE_FOOD = "cl.inacap.smartpet.ACTION_FILL_SAFE_FOOD"
        const val ACTION_REFILL_MANUAL_WATER = "cl.inacap.smartpet.ACTION_REFILL_MANUAL_WATER"
        const val ACTION_CLEAN_WATER_DONE = "cl.inacap.smartpet.ACTION_CLEAN_WATER_DONE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val db = BaseDatos(context)
        val enlace = Sesion.enlaceActivo.value

        when (intent.action) {
            ACTION_DISPENSE_40 -> {
                val anterior = Sesion.nivelAlimento.value ?: 15.0
                val nuevo = minOf(100.0, anterior + 40.0)
                Sesion.actualizarNivel(nuevo)
                db.registrarReporte("comida", anterior, nuevo, "Dispensado rápido (40g) desde notificación")
                CoroutineScope(Dispatchers.IO).launch {
                    enlace?.enviar(Mensaje("CMD", "dispense", "ON"))
                }
                Toast.makeText(context, "🐾 Ración de 40g dispensada", Toast.LENGTH_SHORT).show()
            }
            ACTION_FILL_SAFE_FOOD -> {
                val anterior = Sesion.nivelAlimento.value ?: 15.0
                val nuevo = 85.0
                Sesion.actualizarNivel(nuevo)
                db.registrarReporte("comida", anterior, nuevo, "Llenado a límite seguro (85g) desde notificación")
                CoroutineScope(Dispatchers.IO).launch {
                    enlace?.enviar(Mensaje("CMD", "dispense", "ON"))
                }
                Toast.makeText(context, "🐾 Tolva llenada a límite seguro (85g)", Toast.LENGTH_SHORT).show()
            }
            ACTION_REFILL_MANUAL_WATER -> {
                val anterior = Sesion.nivelAgua.value ?: 0.0
                val nuevo = 95.0
                Sesion.actualizarNivelAgua(nuevo)
                Sesion.registrarLimpiezaAgua(context)
                db.registrarReporte("agua", anterior, nuevo, "Estanque rellenado manualmente")
                CoroutineScope(Dispatchers.IO).launch {
                    enlace?.enviar(Mensaje("CMD", "refill_water", "ON"))
                }
                Toast.makeText(context, "💧 Relleno manual de estanque registrado", Toast.LENGTH_SHORT).show()
            }
            ACTION_CLEAN_WATER_DONE -> {
                Sesion.registrarLimpiezaAgua(context)
                db.registrarReporte("agua", Sesion.nivelAgua.value ?: 90.0, 95.0, "Limpieza de estanque y recambio de agua")
                Toast.makeText(context, "💧 Estanque registrado como limpio y agua fresca", Toast.LENGTH_SHORT).show()
            }
        }

        // Descartar notificación luego de la acción
        if (notifId != -1) {
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }
}
