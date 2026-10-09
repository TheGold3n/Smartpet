package cl.inacap.smartpet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISPENSE_40 = "cl.inacap.smartpet.ACTION_DISPENSE_40"
        const val ACTION_FILL_SAFE_FOOD = "cl.inacap.smartpet.ACTION_FILL_SAFE_FOOD"
        const val ACTION_REFILL_MANUAL_WATER = "cl.inacap.smartpet.ACTION_REFILL_MANUAL_WATER"
        const val ACTION_CLEAN_WATER_DONE = "cl.inacap.smartpet.ACTION_CLEAN_WATER_DONE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (!Sesion.puedeControlar) {
            Toast.makeText(context, "Abre SmartPet con acceso de operador y conecta el nodo", Toast.LENGTH_LONG).show()
            return
        }
        when (intent.action) {
            ACTION_DISPENSE_40 -> Sesion.enviarComando("dispense", "40")
            ACTION_FILL_SAFE_FOOD -> {
                val gramos = (85.0 - (Sesion.nivelAlimento.value ?: return)).coerceIn(0.0, 60.0)
                if (gramos >= 1) Sesion.enviarComando("dispense", "%.1f".format(java.util.Locale.US, gramos))
            }
            ACTION_REFILL_MANUAL_WATER -> Sesion.enviarComando("refill_water", "ON")
            ACTION_CLEAN_WATER_DONE -> Sesion.registrarLimpiezaAgua(context)
            else -> return
        }
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (id != -1) NotificationManagerCompat.from(context).cancel(id)
    }
}
