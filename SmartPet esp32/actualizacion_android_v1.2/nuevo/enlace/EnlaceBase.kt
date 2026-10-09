package cl.inacap.smartpet.enlace

import cl.inacap.smartpet.seguridad.Cripto
import cl.inacap.smartpet.seguridad.Permisos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.PrintWriter
import java.io.IOException
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

abstract class EnlaceBase(
    private val pin: String,
    protected val servidor: Boolean,
    private val pinLectura: String? = null,
    private val rolLocal: String = "observador"
) : Enlace {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    protected val _estado = MutableStateFlow(if (servidor) "Iniciando nodo..." else "Conectando...")
    protected val _detalle = MutableStateFlow("")
    private val _acceso = MutableStateFlow("")
    override val estado = _estado.asStateFlow()
    override val detalle = _detalle.asStateFlow()
    override val acceso = _acceso.asStateFlow()
    private val _entrantes = MutableSharedFlow<Mensaje>(extraBufferCapacity = 32)
    override val entrantes = _entrantes.asSharedFlow()
    private val escritura = Mutex()
    private var writer: PrintWriter? = null
    private var clave: SecretKeySpec? = null
    private var protocolo: ProtocoloSesion? = null
    protected var cerrando = false

    private fun nonce() = UUID.randomUUID().toString().replace("-", "")
    private fun leer(reader: BufferedReader): String {
        val texto = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c == -1) throw IOException("El otro dispositivo cerró el enlace")
            if (c == 10) return texto.toString().trimEnd('\r')
            if (texto.length >= 4096) throw IOException("Trama demasiado larga")
            texto.append(c.toChar())
        }
    }
    private fun escribirInicial(texto: String, key: SecretKeySpec, salida: PrintWriter) {
        salida.println(Cripto.encrypt(texto, key))
        salida.flush()
        if (salida.checkError()) throw IOException("No se pudo enviar el mensaje")
    }

    protected suspend fun atender(reader: BufferedReader, salida: PrintWriter, cerrarSocket: () -> Unit) = coroutineScope {
        var heartbeat: Job? = null
        var vigilancia: Job? = null
        val plazo = launch { delay(10_000); cerrarSocket() }
        try {
            _estado.value = "Verificando PIN..."
            var key = Cripto.deriveAESKey(pin)
            val id: String
            if (servidor) {
                val cifrado = leer(reader)
                var hello = Cripto.decrypt(cifrado, key)
                var capacidad = "operador"
                if (hello == null && pinLectura != null) {
                    key = Cripto.deriveAESKey(pinLectura)
                    hello = Cripto.decrypt(cifrado, key)
                    capacidad = "observador"
                }
                val datos = hello?.split(';') ?: throw IOException("PIN incorrecto")
                if (datos.size != 3 || datos[0] != "HELLO" || datos[1] !in listOf("operador", "observador") ||
                    !datos[2].matches(Regex("[a-f0-9]{32}"))) throw IOException("Vinculación inválida")
                val reto = nonce()
                escribirInicial("CHALLENGE;${datos[2]};$reto", key, salida)
                if (Cripto.decrypt(leer(reader), key) != "AUTH;$reto") throw IOException("PIN o respuesta incorrectos")
                _acceso.value = Permisos.accesoEfectivo(capacidad, datos[1])
                escribirInicial("READY;${_acceso.value}", key, salida)
                id = datos[2] + reto
            } else {
                val propio = nonce()
                escribirInicial("HELLO;$rolLocal;$propio", key, salida)
                val reto = Cripto.decrypt(leer(reader), key)?.split(';') ?: throw IOException("PIN incorrecto")
                if (reto.size != 3 || reto[0] != "CHALLENGE" || reto[1] != propio ||
                    !reto[2].matches(Regex("[a-f0-9]{32}"))) throw IOException("Vinculación inválida")
                escribirInicial("AUTH;${reto[2]}", key, salida)
                val ready = Cripto.decrypt(leer(reader), key)?.split(';') ?: throw IOException("Vinculación inválida")
                if (ready.size != 2 || ready[0] != "READY" || ready[1] !in listOf("operador", "observador"))
                    throw IOException("Acceso no confirmado")
                _acceso.value = ready[1]
                id = propio + reto[2]
            }
            plazo.cancel()
            clave = key
            protocolo = ProtocoloSesion(id, servidor)
            writer = salida
            _detalle.value = "PIN verificado · acceso ${_acceso.value}"
            _estado.value = "Conectado"
            val ultimaRecepcion = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
            heartbeat = launch {
                while (isActive) {
                    delay(2000)
                    try { enviar(Mensaje("PING", "enlace", "OK")) }
                    catch (_: Exception) { cerrarSocket(); break }
                }
            }
            vigilancia = launch {
                while (isActive) {
                    delay(1000)
                    if (System.nanoTime() - ultimaRecepcion.get() > 8_000_000_000L) {
                        _detalle.value = "Sin respuesta durante 8 segundos"
                        cerrarSocket()
                        break
                    }
                }
            }
            while (isActive && !cerrando) {
                val texto = Cripto.decrypt(leer(reader), key) ?: throw IOException("Trama no autenticada")
                val mensaje = protocolo?.recibir(texto) ?: throw IOException("Trama inválida o repetida")
                ultimaRecepcion.set(System.nanoTime())
                if (mensaje.tipo == "PING") continue
                if (servidor && mensaje.tipo == "CMD" && _acceso.value != "operador") {
                    enviar(Mensaje("ERROR", mensaje.clave, "Permiso de operador requerido"))
                } else if (servidor && mensaje.tipo != "CMD") {
                    throw IOException("Mensaje no admitido por el nodo")
                } else {
                    _entrantes.emit(mensaje)
                }
            }
        } catch (e: Exception) {
            if (!cerrando) _detalle.value = e.message ?: "Error de conexión"
        } finally {
            plazo.cancel()
            heartbeat?.cancel()
            vigilancia?.cancel()
            writer = null
            clave = null
            protocolo = null
            _acceso.value = ""
            _estado.value = "Desconectado"
            cerrarSocket()
        }
    }

    override suspend fun enviar(mensaje: Mensaje) {
        withContext(Dispatchers.IO) {
            escritura.withLock {
                check(_estado.value == "Conectado") { "Estación desconectada" }
                if (!servidor && mensaje.tipo == "CMD") {
                    check(rolLocal == "operador" && _acceso.value == "operador") { "Acceso de solo lectura" }
                }
                val key = clave ?: throw IOException("Clave no disponible")
                val trama = protocolo?.empaquetar(mensaje) ?: throw IOException("Sesión no disponible")
                val salida = writer ?: throw IOException("Enlace no disponible")
                salida.println(Cripto.encrypt(trama, key))
                salida.flush()
                if (salida.checkError()) throw IOException("No se pudo enviar el mensaje")
            }
        }
    }
}
