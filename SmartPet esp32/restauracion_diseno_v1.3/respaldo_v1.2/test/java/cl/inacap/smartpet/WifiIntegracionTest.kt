package cl.inacap.smartpet

import cl.inacap.smartpet.enlace.*
import cl.inacap.smartpet.seguridad.Cripto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.*
import java.util.concurrent.atomic.AtomicInteger

class WifiIntegracionTest {
    private fun puerto() = ServerSocket(0).use { it.localPort }
    private suspend fun conectado(e: Enlace) { withTimeout(6000) { e.estado.first { it == "Conectado" } } }
    private fun socket(p: Int): Socket {
        repeat(50) { try { return Socket("127.0.0.1",p).apply { soTimeout = 4000 } } catch (_: IOException) { Thread.sleep(20) } }
        error("Servidor no disponible")
    }
    @Test fun wifiReceivesTelemetryAndOperatorCommand() = runBlocking {
        val p = puerto()
        val nodo = WifiEnlace("234567",true,pinLectura="765432",puerto=p)
        withTimeout(4000) { nodo.estado.first { it == "Esperando panel" } }
        val panel = WifiEnlace("234567",false,"127.0.0.1",rolLocal="operador",puerto=p)
        try {
            conectado(nodo); conectado(panel)
            assertEquals("operador",panel.acceso.value)
            val lectura = async(start=CoroutineStart.UNDISPATCHED) { withTimeout(4000) { panel.entrantes.first { it.tipo == "LECTURA" } } }
            nodo.enviar(Mensaje("LECTURA","agua","35.5"))
            assertEquals(Mensaje("LECTURA","agua","35.5"),lectura.await())
            val orden = async(start=CoroutineStart.UNDISPATCHED) { withTimeout(4000) { nodo.entrantes.first() } }
            panel.enviar(Mensaje("CMD","dispense","30"))
            assertEquals(Mensaje("CMD","dispense","30"),orden.await())
        } finally { panel.cerrar(); nodo.cerrar() }
    }
    @Test fun manipulatedObserverClientCannotSendCommandToNode() = runBlocking {
        val p = puerto()
        val nodo = WifiEnlace("234567",true,pinLectura="765432",puerto=p)
        val recibidos = AtomicInteger()
        val recoger = launch(Dispatchers.IO) { nodo.entrantes.collect { if (it.tipo == "CMD") recibidos.incrementAndGet() } }
        try {
            socket(p).use { s ->
                val input = s.getInputStream().bufferedReader()
                val output = PrintWriter(s.getOutputStream(),true)
                val key = Cripto.deriveAESKey("765432")
                val nonce = "a".repeat(32)
                output.println(Cripto.encrypt("HELLO;operador;$nonce",key))
                val challenge = Cripto.decrypt(input.readLine(),key)!!.split(';')
                output.println(Cripto.encrypt("AUTH;${challenge[2]}",key))
                assertEquals("READY;observador",Cripto.decrypt(input.readLine(),key))
                val codec = ProtocoloSesion(nonce+challenge[2],false)
                output.println(Cripto.encrypt(codec.empaquetar(Mensaje("CMD","refill_water","ON")),key))
                var response: Mensaje?
                do { response = codec.recibir(Cripto.decrypt(input.readLine(),key)!!) } while (response?.tipo == "PING")
                assertEquals("ERROR",response?.tipo)
                assertEquals(0,recibidos.get())
            }
        } finally { recoger.cancel(); nodo.cerrar() }
    }
    @Test fun wrongPinNeverReachesConnectedState() = runBlocking {
        val p = puerto()
        val nodo = WifiEnlace("234567",true,pinLectura="765432",puerto=p)
        withTimeout(4000) { nodo.estado.first { it == "Esperando panel" } }
        val panel = WifiEnlace("111111",false,"127.0.0.1",rolLocal="operador",puerto=p)
        try {
            withTimeout(6000) { panel.estado.first { it == "Desconectado" } }
            assertEquals("",panel.acceso.value)
            assertNotEquals("Conectado",nodo.estado.value)
        } finally { panel.cerrar(); nodo.cerrar() }
    }
    @Test fun nodeAcceptsNewPanelAfterDisconnect() = runBlocking {
        val p = puerto()
        val nodo = WifiEnlace("234567",true,pinLectura="765432",puerto=p)
        withTimeout(4000) { nodo.estado.first { it == "Esperando panel" } }
        var panel = WifiEnlace("234567",false,"127.0.0.1",rolLocal="operador",puerto=p)
        try {
            conectado(panel)
            panel.cerrar()
            delay(700)
            panel = WifiEnlace("765432",false,"127.0.0.1",rolLocal="observador",puerto=p)
            conectado(panel)
            assertEquals("observador",panel.acceso.value)
            var rechazado = false
            try { panel.enviar(Mensaje("CMD","pump","ON")) } catch (_: IllegalStateException) { rechazado = true }
            assertTrue(rechazado)
        } finally { panel.cerrar(); nodo.cerrar() }
    }
}
