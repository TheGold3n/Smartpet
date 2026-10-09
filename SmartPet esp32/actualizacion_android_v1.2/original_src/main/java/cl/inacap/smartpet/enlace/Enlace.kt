package cl.inacap.smartpet.enlace

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class Mensaje(val tipo: String, val clave: String, val valor: String) {
    companion object {
        fun parse(line: String): Mensaje? {
            val parts = line.split(";")
            if (parts.size == 3) {
                return Mensaje(parts[0], parts[1], parts[2])
            }
            return null
        }
    }
    
    override fun toString(): String {
        return "$tipo;$clave;$valor"
    }
}

interface Enlace {
    val estado: StateFlow<String>
    val entrantes: SharedFlow<Mensaje>
    suspend fun enviar(mensaje: Mensaje)
    fun cerrar()
}
