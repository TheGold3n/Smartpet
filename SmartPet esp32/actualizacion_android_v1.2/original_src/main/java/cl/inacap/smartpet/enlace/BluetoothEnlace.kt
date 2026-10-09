package cl.inacap.smartpet.enlace

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothEnlace(private val pin: String, isServer: Boolean, device: BluetoothDevice? = null, adapter: BluetoothAdapter? = null) : EnlaceBase(pin) {
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isServer && adapter != null) {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord("SmartPet", uuid)
                    socket = serverSocket?.accept()
                    serverSocket?.close()
                } else if (!isServer && device != null) {
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket?.connect()
                }
                
                socket?.let {
                    reader = BufferedReader(InputStreamReader(it.inputStream))
                    writer = PrintWriter(OutputStreamWriter(it.outputStream))
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
