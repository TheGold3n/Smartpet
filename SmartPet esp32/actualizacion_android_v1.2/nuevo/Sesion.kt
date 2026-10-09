package cl.inacap.smartpet

import android.content.Context
import cl.inacap.smartpet.datos.BaseDatos
import cl.inacap.smartpet.enlace.*
import cl.inacap.smartpet.seguridad.Permisos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.SecureRandom

object Sesion {
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var gestor: Job? = null
    private var dispensado: Job? = null
    private var bombaTimeout: Job? = null
    private var esperaAck: Job? = null
    private val simulador = Simulador()
    var ultimoPin = ""
    var ultimaIp = ""
    var medioWifi = true
    var direccionBluetooth = ""
    var modoNodo = false
        private set
    var usuarioActual = ""
        private set
    private val _autenticado = MutableStateFlow(false)
    val autenticado = _autenticado.asStateFlow()
    private val _rol = MutableStateFlow("observador")
    val rol = _rol.asStateFlow()
    private val _nivelAlimento = MutableStateFlow<Double?>(null)
    val nivelAlimento = _nivelAlimento.asStateFlow()
    private val _nivelAgua = MutableStateFlow<Double?>(null)
    val nivelAgua = _nivelAgua.asStateFlow()
    private val _dispensadorActivo = MutableStateFlow(false)
    val dispensadorActivo = _dispensadorActivo.asStateFlow()
    private val _bombaActiva = MutableStateFlow(false)
    val bombaActiva = _bombaActiva.asStateFlow()
    private val _enlaceActivo = MutableStateFlow<Enlace?>(null)
    val enlaceActivo = _enlaceActivo.asStateFlow()
    private val _ultimoCambioAgua = MutableStateFlow(System.currentTimeMillis())
    val ultimoCambioAgua = _ultimoCambioAgua.asStateFlow()
    private val _mensaje = MutableStateFlow("")
    val mensaje = _mensaje.asStateFlow()
    private val _pendiente = MutableStateFlow(false)
    val pendiente = _pendiente.asStateFlow()
    private var comandoEsperado = ""
    val umbralCritico = 20.0
    val esOperador get() = _autenticado.value && _rol.value == "operador"
    val puedeControlar get() = !modoNodo && Permisos.puedeControlar(_autenticado.value, _rol.value,
        _enlaceActivo.value?.acceso?.value ?: "", _enlaceActivo.value?.estado?.value == "Conectado")

    fun inicializar(appContext: Context) {
        context = appContext.applicationContext
        cargarLimpiezaAgua(context)
    }
    private fun codigo() = (100000 + SecureRandom().nextInt(900000)).toString()
    fun pinNodo(lectura: Boolean = false): String {
        val prefs = context.getSharedPreferences("smartpet_prefs", Context.MODE_PRIVATE)
        val key = if (lectura) "pin_nodo_lectura" else "pin_nodo_control"
        val existente = prefs.getString(key, null)
        if (existente != null) return existente
        var nuevo = codigo()
        if (lectura) while (nuevo == pinNodo()) nuevo = codigo()
        prefs.edit().putString(key, nuevo).apply()
        return nuevo
    }
    fun autenticar(usuario: String, pass: String): Boolean {
        val resultado = BaseDatos(context).use { it.autenticar(usuario.trim(), pass) } ?: return false
        usuarioActual = usuario.trim()
        _rol.value = resultado
        _autenticado.value = true
        return true
    }
    fun actualizarUsuario(usuario: String) { check(_autenticado.value); usuarioActual = usuario }
    fun cerrarSesion() {
        desconectar()
        ultimoPin = ""
        modoNodo = false
        _autenticado.value = false
        _rol.value = "observador"
        usuarioActual = ""
    }
    fun desconectar() {
        alertaComida = false
        alertaAgua = false
        gestor?.cancel()
        esperaAck?.cancel()
        dispensado?.cancel()
        bombaTimeout?.cancel()
        _enlaceActivo.value?.cerrar()
        _enlaceActivo.value = null
        simulador.detener()
        _nivelAlimento.value = null
        _nivelAgua.value = null
        _dispensadorActivo.value = false
        _bombaActiva.value = false
        _pendiente.value = false
        _mensaje.value = ""
    }
    fun setEnlace(enlace: Enlace, nodo: Boolean = false) {
        if (!_autenticado.value || (nodo && !esOperador)) { enlace.cerrar(); return }
        desconectar()
        modoNodo = nodo
        _enlaceActivo.value = enlace
        if (nodo) publicarNodo()
        gestor = scope.launch {
            launch {
                enlace.estado.collect { estado ->
                    if (estado != "Conectado") {
                        esperaAck?.cancel(); _pendiente.value = false
                        dispensado?.cancel(); bombaTimeout?.cancel()
                        simulador.detener()
                        _dispensadorActivo.value = false; _bombaActiva.value = false
                        if (nodo) publicarNodo() else {
                            _nivelAlimento.value = null; _nivelAgua.value = null
                        }
                    }
                }
            }
            launch { enlace.entrantes.collect { if (nodo) procesarNodo(enlace, it) else procesarPanel(it) } }
            if (nodo) launch {
                while (isActive) {
                    if (enlace.estado.value == "Conectado") enviarLecturas(enlace)
                    delay(2000)
                }
            }
        }
    }
    private fun publicarNodo() {
        val estado = simulador.actual()
        _nivelAlimento.value = estado.peso; _nivelAgua.value = estado.agua
        _dispensadorActivo.value = estado.dispensador; _bombaActiva.value = estado.bomba
    }
    fun ajustarPeso(valor: Float) { if (modoNodo) { simulador.ajustarPeso(valor.toDouble()); publicarNodo() } }
    fun ajustarAgua(valor: Float) { if (modoNodo) { simulador.ajustarAgua(valor.toDouble()); publicarNodo() } }
    private suspend fun enviarSeguro(enlace: Enlace, mensaje: Mensaje) {
        try { enlace.enviar(mensaje) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
    }
    private suspend fun enviarLecturas(enlace: Enlace) {
        val estado = simulador.actual()
        enviarSeguro(enlace, Mensaje("LECTURA", "peso", "%.1f".format(java.util.Locale.US, estado.peso)))
        enviarSeguro(enlace, Mensaje("LECTURA", "agua", "%.1f".format(java.util.Locale.US, estado.agua)))
        enviarSeguro(enlace, Mensaje("ESTADO", "dispense", if (estado.dispensador) "ON" else "OFF"))
        enviarSeguro(enlace, Mensaje("ESTADO", "pump", if (estado.bomba) "ON" else "OFF"))
    }
    private suspend fun procesarNodo(enlace: Enlace, msg: Mensaje) {
        if (msg.tipo != "CMD") return
        val resultado = simulador.ejecutar(msg.clave, msg.valor, enlace.acceso.value == "operador")
        publicarNodo()
        if (!resultado.aceptado) {
            enviarSeguro(enlace, Mensaje("ERROR", msg.clave, resultado.detalle)); return
        }
        enviarSeguro(enlace, Mensaje("ACK", msg.clave, resultado.detalle))
        when (msg.clave) {
            "dispense" -> {
                dispensado?.cancel()
                dispensado = scope.launch {
                    delay(1200)
                    simulador.completarDispensado(); publicarNodo()
                    enviarSeguro(enlace, Mensaje("ACK", "dispense", "COMPLETED"))
                    enviarLecturas(enlace)
                }
            }
            "pump" -> {
                bombaTimeout?.cancel()
                if (msg.valor == "ON") bombaTimeout = scope.launch {
                    delay(60_000)
                    simulador.ejecutar("pump", "OFF", true); publicarNodo()
                    enviarSeguro(enlace, Mensaje("ACK", "pump", "TIMEOUT"))
                    enviarLecturas(enlace)
                }
            }
            "emergency_stop" -> { dispensado?.cancel(); bombaTimeout?.cancel() }
        }
        enviarLecturas(enlace)
    }
    private var alertaComida = false
    private var alertaAgua = false
    private suspend fun procesarPanel(msg: Mensaje) {
        when (msg.tipo) {
            "LECTURA" -> {
                val valor = msg.valor.toDoubleOrNull() ?: return
                if (!valor.isFinite() || valor !in 0.0..100.0 || msg.clave !in listOf("peso", "agua")) return
                val anterior = if (msg.clave == "peso") _nivelAlimento.value else _nivelAgua.value
                if (msg.clave == "peso") _nivelAlimento.value = valor else _nivelAgua.value = valor
                withContext(Dispatchers.IO) { BaseDatos(context).use { db ->
                    db.guardarLectura(msg.clave, valor)
                    if (anterior != null && kotlin.math.abs(valor - anterior) >= 0.1) db.registrarReporte(
                        if (msg.clave == "peso") "comida" else "agua", anterior, valor,
                        if (valor < anterior) "Consumo simulado" else "Nivel simulado aumentado")
                } }
                if (msg.clave == "peso") {
                    if (valor < 20 && !alertaComida) Notificaciones.mostrarAlertaComida(context, 2, valor, usuarioActual)
                    alertaComida = valor < 20
                } else {
                    if (valor < 20 && !alertaAgua) Notificaciones.mostrarAlertaAgua(context, 3, valor, usuarioActual)
                    alertaAgua = valor < 20
                }
            }
            "ESTADO" -> when (msg.clave) {
                "dispense" -> _dispensadorActivo.value = msg.valor == "ON"
                "pump" -> _bombaActiva.value = msg.valor == "ON"
            }
            "ACK", "ERROR" -> {
                _mensaje.value = when {
                    msg.tipo == "ERROR" -> "Rechazado: ${msg.valor}"
                    msg.clave == "dispense" && msg.valor == "IN_PROGRESS" -> "Dispensando..."
                    msg.clave == "dispense" -> "Dispensado simulado completado"
                    msg.clave == "refill_water" -> "Relleno simulado confirmado por el nodo"
                    msg.clave == "pump" && msg.valor == "TIMEOUT" -> "Bomba apagada por límite de 60 segundos"
                    msg.clave == "pump" -> "Bomba ${if (msg.valor == "ON") "encendida" else "apagada"}"
                    else -> "Parada confirmada por el nodo"
                }
                if (msg.clave == comandoEsperado && msg.valor != "IN_PROGRESS") {
                    esperaAck?.cancel(); _pendiente.value = false
                }
            }
        }
    }
    fun enviarComando(clave: String, valor: String) {
        if (!puedeControlar) { _mensaje.value = "Se requiere operador, PIN de control y conexión activa"; return }
        if (_pendiente.value && clave != "emergency_stop") { _mensaje.value = "Espera la confirmación de la orden anterior"; return }
        esperaAck?.cancel()
        val enlace = _enlaceActivo.value ?: return
        _pendiente.value = true; comandoEsperado = clave
        _mensaje.value = "Esperando confirmación del nodo..."
        esperaAck = scope.launch {
            try { enlace.enviar(Mensaje("CMD", clave, valor)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _mensaje.value = e.message ?: "No se pudo enviar"; _pendiente.value = false; return@launch
            }
            delay(7000)
            _pendiente.value = false; _mensaje.value = "El nodo no confirmó la orden"
        }
    }
    fun registrarLimpiezaAgua(context: Context) {
        if (!puedeControlar) return
        val now = System.currentTimeMillis()
        _ultimoCambioAgua.value = now
        context.getSharedPreferences("smartpet_prefs", Context.MODE_PRIVATE).edit().putLong("ultimo_cambio_agua", now).apply()
    }
    fun cargarLimpiezaAgua(context: Context) {
        val prefs = context.getSharedPreferences("smartpet_prefs", Context.MODE_PRIVATE)
        _ultimoCambioAgua.value = prefs.getLong("ultimo_cambio_agua", System.currentTimeMillis())
    }
}
