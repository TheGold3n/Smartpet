package cl.inacap.smartpet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import cl.inacap.smartpet.enlace.Enlace

object Sesion {
    var ultimoPin: String = "123456"
    var ultimaIp: String = "127.0.0.1"
    var usuarioActual: String = "admin"

    private val _nivelAlimento = MutableStateFlow<Double?>(null)
    val nivelAlimento = _nivelAlimento.asStateFlow()

    private val _nivelAgua = MutableStateFlow<Double?>(null)
    val nivelAgua = _nivelAgua.asStateFlow()

    private val _dispensadorActivo = MutableStateFlow(false)
    val dispensadorActivo = _dispensadorActivo.asStateFlow()

    private val _bombaActiva = MutableStateFlow(false)
    val bombaActiva = _bombaActiva.asStateFlow()

    private val _rol = MutableStateFlow("operador")
    val rol = _rol.asStateFlow()

    private val _enlaceActivo = MutableStateFlow<Enlace?>(null)
    val enlaceActivo = _enlaceActivo.asStateFlow()

    private val _ultimoCambioAgua = MutableStateFlow<Long>(System.currentTimeMillis())
    val ultimoCambioAgua = _ultimoCambioAgua.asStateFlow()
    
    val umbralCritico = 20.0

    fun actualizarNivel(nivel: Double?) {
        _nivelAlimento.value = nivel
    }

    fun actualizarNivelAgua(nivel: Double?) {
        _nivelAgua.value = nivel
    }

    fun setDispensadorActivo(activo: Boolean) {
        _dispensadorActivo.value = activo
    }

    fun setBombaActiva(activa: Boolean) {
        _bombaActiva.value = activa
    }

    fun setRol(nuevoRol: String) {
        _rol.value = nuevoRol
    }

    fun setEnlace(enlace: Enlace?) {
        _enlaceActivo.value = enlace
    }

    fun registrarLimpiezaAgua(context: Context) {
        val now = System.currentTimeMillis()
        _ultimoCambioAgua.value = now
        val prefs = context.getSharedPreferences("smartpet_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("ultimo_cambio_agua", now).apply()
    }

    fun cargarLimpiezaAgua(context: Context) {
        val prefs = context.getSharedPreferences("smartpet_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getLong("ultimo_cambio_agua", System.currentTimeMillis())
        _ultimoCambioAgua.value = saved
    }

    fun simularAlerta3Dias(context: Context) {
        val tresDiasAtras = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L + 10000)
        _ultimoCambioAgua.value = tresDiasAtras
        Notificaciones.mostrarAlertaCambioAgua(context, usuarioActual, 3)
    }
}
