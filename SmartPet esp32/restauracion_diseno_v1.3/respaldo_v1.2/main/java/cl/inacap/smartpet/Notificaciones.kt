package cl.inacap.smartpet

import android.app.*
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
        val channel = NotificationChannel(CHANNEL_ID, "Alertas SmartPet", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lecturas y estado de la simulación SmartPet"
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
    private fun publicar(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
    private fun builder(context: Context, titulo: String, texto: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(titulo).setContentText(texto)
        .setStyle(NotificationCompat.BigTextStyle().bigText(texto)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
    private fun accion(context: Context, accion: String, id: Int, request: Int): PendingIntent = PendingIntent.getBroadcast(context, request,
        Intent(context, NotificationActionReceiver::class.java).apply { action = accion; putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, id) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun mostrar(context: Context, id: Int, titulo: String, texto: String) { publicar(context, id, builder(context, titulo, texto)) }
    fun mostrarAlertaComida(context: Context, id: Int, actualGramos: Double, usuario: String = "Amigo") {
        val b = builder(context, "🐾 $usuario, queda poca comida", "Lectura del nodo: %.1f g en el plato (simulación).".format(actualGramos))
        if (Sesion.puedeControlar) b.addAction(android.R.drawable.ic_input_add, "Solicitar 40 g",
            accion(context, NotificationActionReceiver.ACTION_DISPENSE_40, id, 101))
        publicar(context, id, b)
    }
    fun mostrarAlertaAgua(context: Context, id: Int, actualPorcentaje: Double, usuario: String = "Amigo") {
        val b = builder(context, "💧 $usuario, queda poca agua", "Lectura del nodo: %.1f %% (simulación).".format(actualPorcentaje))
        if (Sesion.puedeControlar) b.addAction(android.R.drawable.ic_input_add, "Simular relleno",
            accion(context, NotificationActionReceiver.ACTION_REFILL_MANUAL_WATER, id, 201))
        publicar(context, id, b)
    }
    fun mostrarAlertaCambioAgua(context: Context, usuario: String = "Amigo", diasTranscurridos: Int = 3) {
        val b = builder(context, "💧 Recordatorio de limpieza", "$usuario: han pasado $diasTranscurridos días desde el registro de limpieza.")
        if (Sesion.puedeControlar) b.addAction(android.R.drawable.ic_menu_save, "Registrar limpieza",
            accion(context, NotificationActionReceiver.ACTION_CLEAN_WATER_DONE, 4, 301))
        publicar(context, 4, b)
    }
}
