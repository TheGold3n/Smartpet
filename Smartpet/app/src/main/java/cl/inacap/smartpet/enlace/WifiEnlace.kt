package cl.inacap.smartpet.enlace

import kotlinx.coroutines.*
import java.io.*
import java.net.*

class WifiEnlace(
    pin: String, isServer: Boolean, ip: String = "", pinLectura: String? = null,
    rolLocal: String = "observador", private val puerto: Int = 5050
) : EnlaceBase(pin, isServer, pinLectura, rolLocal) {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    companion object {
        fun getLocalIpAddress(): String = try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            val preferidas = interfaces.sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap")) 0 else 1 }
            preferidas.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress ?: ""
        } catch (_: Exception) { "" }
    }
    init {
        scope.launch {
            try {
                if (servidor) {
                    serverSocket = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(puerto)) }
                    while (isActive && !cerrando) {
                        _estado.value = "Esperando panel"
                        socket = serverSocket!!.accept()
                        atenderSocket(socket!!)
                        if (!cerrando) delay(500)
                    }
                } else {
                    socket = Socket()
                    socket!!.connect(InetSocketAddress(ip, puerto), 5000)
                    atenderSocket(socket!!)
                }
            } catch (e: Exception) {
                if (!cerrando) _detalle.value = e.message ?: "No se pudo conectar"
                _estado.value = "Desconectado"
            }
        }
    }
    private suspend fun atenderSocket(actual: Socket) {
        actual.tcpNoDelay = true
        atender(BufferedReader(InputStreamReader(actual.getInputStream(), Charsets.UTF_8)),
            PrintWriter(OutputStreamWriter(actual.getOutputStream(), Charsets.UTF_8)), { runCatching { actual.close() } })
    }
    override fun cerrar() {
        cerrando = true
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        scope.cancel()
        _estado.value = "Desconectado"
    }
}
