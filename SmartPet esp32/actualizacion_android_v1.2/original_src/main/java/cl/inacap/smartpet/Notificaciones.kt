package cl.inacap.smartpet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notificaciones {
    private const val CHANNEL_ID = "smartpet_alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas SmartPet"
            val descriptionText = "Notificaciones de cuidado y estado de SmartPet"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun mostrar(context: Context, id: Int, titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
    }

    // --- ALERTA DE COMIDA BAJA: LA MASCOTA HABLA PIDIENDO COMIDA ---
    fun mostrarAlertaComida(context: Context, id: Int, actualGramos: Double, usuario: String = "Amigo") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val intent40 = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISPENSE_40
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, id)
        }
        val pIntent40 = PendingIntent.getBroadcast(context, 101, intent40, flags)

        val intent85 = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_FILL_SAFE_FOOD
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, id)
        }
        val pIntent85 = PendingIntent.getBroadcast(context, 102, intent85, flags)

        val nombre = if (usuario.isNotEmpty()) usuario else "Humano"
        val titulo = "🐾 ¡Hola $nombre! Me estoy quedando sin comida..."
        val mensaje = "¿Me puedes alimentar más? (Dame comida) Quedan solo ${String.format(java.util.Locale.US, "%.1f", actualGramos)} g en la tolva."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_input_add, "Dispensar (40g)", pIntent40)
            .addAction(android.R.drawable.ic_menu_upload, "Llenar a límite seguro (85g máx)", pIntent85)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
    }

    // --- ALERTA DE AGUA CRÍTICA EN EL ESTANQUE: LA MASCOTA PIDE AGUA ---
    fun mostrarAlertaAgua(context: Context, id: Int, actualPorcentaje: Double, usuario: String = "Amigo") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val intentRelleno = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REFILL_MANUAL_WATER
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, id)
        }
        val pIntentRelleno = PendingIntent.getBroadcast(context, 201, intentRelleno, flags)

        val nombre = if (usuario.isNotEmpty()) usuario else "Humano"
        val titulo = "💧 ¡Hola $nombre! Queda poca agua, repón más"
        val mensaje = "Nivel del agua del estanque en límites críticos (${String.format(java.util.Locale.US, "%.0f", actualPorcentaje)}%), cambiar y rellenar de forma urgente a mano."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_input_add, "Ya rellené el estanque", pIntentRelleno)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
    }

    // --- RECORDATORIO HIGIENE: CAMBIO DE AGUA CADA 3-4 DÍAS ---
    fun mostrarAlertaCambioAgua(context: Context, usuario: String = "Amigo", diasTranscurridos: Int = 3) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val intentLimpio = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CLEAN_WATER_DONE
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 4)
        }
        val pIntentLimpio = PendingIntent.getBroadcast(context, 301, intentLimpio, flags)

        val nombre = if (usuario.isNotEmpty()) usuario else "Humano"
        val titulo = "💧 ¡Hola $nombre! Recuerda cambiar mi agua"
        val mensaje = "Han pasado $diasTranscurridos días desde el último recambio. Recuerda limpiar el estanque y renovar el agua para que siga fresca y limpia."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_save, "Estanque Limpiado", pIntentLimpio)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(4, builder.build())
        }
    }
}
