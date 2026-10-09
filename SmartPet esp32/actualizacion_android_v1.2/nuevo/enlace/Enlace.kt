package cl.inacap.smartpet.enlace

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class Mensaje(val tipo: String, val clave: String, val valor: String) {
    companion object {
        fun parse(line: String): Mensaje? {
            val parts = line.split(';')
            if (parts.size != 3 || parts.any { it.isEmpty() || it.length > 128 } ||
                parts.any { '\n' in it || '\r' in it }) return null
            return Mensaje(parts[0], parts[1], parts[2])
        }
    }
    override fun toString() = "$tipo;$clave;$valor"
}

interface Enlace {
    val estado: StateFlow<String>
    val detalle: StateFlow<String>
    val acceso: StateFlow<String>
    val entrantes: SharedFlow<Mensaje>
    suspend fun enviar(mensaje: Mensaje)
    fun cerrar()
}
