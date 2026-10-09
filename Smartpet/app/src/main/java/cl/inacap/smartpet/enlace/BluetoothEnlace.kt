package cl.inacap.smartpet.enlace

import android.annotation.SuppressLint
import android.bluetooth.*
import kotlinx.coroutines.*
import java.io.*
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothEnlace(
    pin: String, isServer: Boolean, device: BluetoothDevice? = null, adapter: BluetoothAdapter? = null,
    pinLectura: String? = null, rolLocal: String = "observador"
) : EnlaceBase(pin, isServer, pinLectura, rolLocal) {
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    init {
        scope.launch {
            try {
                check(adapter != null && adapter.isEnabled) { "Activa Bluetooth y concede el permiso" }
                if (servidor) {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord("SmartPet", uuid)
                    while (isActive && !cerrando) {
                        _estado.value = "Esperando panel"
                        socket = serverSocket!!.accept()
                        atenderSocket(socket!!)
                        if (!cerrando) delay(500)
                    }
                } else {
                    check(device != null) { "Selecciona un dispositivo emparejado" }
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    val plazo = launch { delay(10_000); runCatching { socket?.close() } }
                    try { socket!!.connect() } finally { plazo.cancel() }
                    atenderSocket(socket!!)
                }
            } catch (e: Exception) {
                if (!cerrando) _detalle.value = e.message ?: "Error de Bluetooth"
                _estado.value = "Desconectado"
            }
        }
    }
    private suspend fun atenderSocket(actual: BluetoothSocket) {
        atender(BufferedReader(InputStreamReader(actual.inputStream, Charsets.UTF_8)),
            PrintWriter(OutputStreamWriter(actual.outputStream, Charsets.UTF_8)), { runCatching { actual.close() } })
    }
    override fun cerrar() {
        cerrando = true
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        scope.cancel()
        _estado.value = "Desconectado"
    }
}
