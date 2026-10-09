package cl.inacap.smartpet

data class EstadoSimulador(
    val peso: Double = 50.0,
    val agua: Double = 90.0,
    val dispensador: Boolean = false,
    val bomba: Boolean = false
)
data class ResultadoComando(val aceptado: Boolean, val detalle: String)

class Simulador {
    private var estado = EstadoSimulador()
    private var porcionPendiente = 0.0
    @Synchronized fun actual() = estado
    @Synchronized fun ajustarPeso(valor: Double) {
        require(valor.isFinite() && valor in 0.0..100.0)
        estado = estado.copy(peso = valor)
    }
    @Synchronized fun ajustarAgua(valor: Double) {
        require(valor.isFinite() && valor in 0.0..100.0)
        estado = estado.copy(agua = valor, bomba = estado.bomba && valor >= 20.0)
    }
    @Synchronized fun ejecutar(clave: String, valor: String, autorizado: Boolean): ResultadoComando {
        if (!autorizado) return ResultadoComando(false, "Permiso de operador requerido")
        return when (clave) {
            "dispense" -> {
                val gramos = if (valor == "ON") 30.0 else valor.toDoubleOrNull()
                when {
                    gramos == null || !gramos.isFinite() || gramos !in 1.0..60.0 -> ResultadoComando(false, "Ración válida: 1 a 60 g")
                    estado.dispensador -> ResultadoComando(false, "Dispensado ya en curso")
                    estado.peso + gramos > 100.0 -> ResultadoComando(false, "La ración supera la capacidad de 100 g")
                    else -> {
                        porcionPendiente = gramos
                        estado = estado.copy(dispensador = true)
                        ResultadoComando(true, "IN_PROGRESS")
                    }
                }
            }
            "pump" -> when {
                valor !in listOf("ON", "OFF") -> ResultadoComando(false, "Estado de bomba inválido")
                valor == "ON" && estado.agua < 20.0 -> ResultadoComando(false, "Agua insuficiente para activar bomba")
                valor == "ON" && estado.bomba -> ResultadoComando(false, "La bomba ya está encendida")
                else -> {
                    estado = estado.copy(bomba = valor == "ON")
                    ResultadoComando(true, valor)
                }
            }
            "refill_water" -> if (valor == "ON") {
                estado = estado.copy(agua = 95.0)
                ResultadoComando(true, "COMPLETED")
            } else ResultadoComando(false, "Relleno inválido")
            "emergency_stop" -> {
                detener()
                ResultadoComando(true, "COMPLETED")
            }
            else -> ResultadoComando(false, "Comando no admitido")
        }
    }
    @Synchronized fun completarDispensado() {
        if (estado.dispensador) estado = estado.copy(peso = (estado.peso + porcionPendiente).coerceAtMost(100.0), dispensador = false)
        porcionPendiente = 0.0
    }
    @Synchronized fun detener() {
        porcionPendiente = 0.0
        estado = estado.copy(dispensador = false, bomba = false)
    }
}
