package cl.inacap.smartpet.enlace

/** Cada conexión tiene un identificador y contadores distintos en cada dirección. */
class ProtocoloSesion(private val id: String, private val servidor: Boolean) {
    private var enviado = 0L
    private var recibido = 0L

    @Synchronized
    fun empaquetar(mensaje: Mensaje): String {
        require(enviado < Long.MAX_VALUE)
        return "V2;$id;${if (servidor) "S" else "C"};${++enviado};$mensaje"
    }

    @Synchronized
    fun recibir(trama: String): Mensaje? {
        val partes = trama.split(';', limit = 5)
        if (partes.size != 5 || partes[0] != "V2" || partes[1] != id ||
            partes[2] != if (servidor) "C" else "S") return null
        val contador = partes[3].toLongOrNull() ?: return null
        if (contador <= recibido) return null
        val mensaje = Mensaje.parse(partes[4]) ?: return null
        recibido = contador
        return mensaje
    }
}
