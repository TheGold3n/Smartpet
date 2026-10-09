package cl.inacap.smartpet.enlace

import cl.inacap.smartpet.seguridad.Cripto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import javax.crypto.spec.SecretKeySpec

abstract class EnlaceBase(pin: String) : Enlace {
    protected val _estado = MutableStateFlow("Conectando...")
    override val estado = _estado.asStateFlow()

    protected val _entrantes = MutableSharedFlow<Mensaje>()
    override val entrantes = _entrantes.asSharedFlow()

    protected var writer: PrintWriter? = null
    protected var reader: BufferedReader? = null
    protected var socketJob: Job? = null
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private val key: SecretKeySpec = Cripto.deriveAESKey(pin)

    protected fun startReading() {
        socketJob = scope.launch {
            try {
                _estado.value = "Conectado"
                while (isActive) {
                    val encryptedLine = reader?.readLine() ?: break
                    val decryptedLine = Cripto.decrypt(encryptedLine, key)
                    if (decryptedLine != null) {
                        val msg = Mensaje.parse(decryptedLine)
                        if (msg != null) {
                            _entrantes.emit(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _estado.value = "Desconectado"
                cerrar()
            }
        }
    }

    override suspend fun enviar(mensaje: Mensaje) {
        withContext(Dispatchers.IO) {
            try {
                val encrypted = Cripto.encrypt(mensaje.toString(), key)
                writer?.println(encrypted)
                writer?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
