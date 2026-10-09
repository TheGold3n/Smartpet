package cl.inacap.smartpet.enlace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class WifiEnlace(private val pin: String, isServer: Boolean, ip: String = "") : EnlaceBase(pin) {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isServer) {
                    serverSocket = ServerSocket(5050)
                    socket = serverSocket?.accept()
                } else {
                    socket = Socket()
                    socket?.connect(InetSocketAddress(ip, 5050), 5000)
                }
                
                socket?.let {
                    reader = BufferedReader(InputStreamReader(it.getInputStream()))
                    writer = PrintWriter(OutputStreamWriter(it.getOutputStream()))
                    startReading()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _estado.value = "Error"
                cerrar()
            }
        }
    }

    override fun cerrar() {
        try {
            socketJob?.cancel()
            writer?.close()
            reader?.close()
            socket?.close()
            serverSocket?.close()
            _estado.value = "Desconectado"
        } catch (e: Exception) {}
    }
}
