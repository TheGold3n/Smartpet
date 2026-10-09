package cl.inacap.smartpet.seguridad

object Permisos {
    fun puedeControlar(autenticado: Boolean, rolLocal: String, accesoNodo: String, conectado: Boolean) =
        autenticado && rolLocal == "operador" && accesoNodo == "operador" && conectado

    fun accesoEfectivo(capacidadPin: String, rolSolicitado: String): String =
        if (capacidadPin == "operador" && rolSolicitado == "operador") "operador" else "observador"
}
