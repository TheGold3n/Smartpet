package cl.inacap.smartpet

import cl.inacap.smartpet.enlace.*
import cl.inacap.smartpet.seguridad.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SeguridadTest {
    @Test fun bothLocalRoleAndNodePermissionAreRequired() {
        assertTrue(Permisos.puedeControlar(true,"operador","operador",true))
        assertFalse(Permisos.puedeControlar(false,"operador","operador",true))
        assertFalse(Permisos.puedeControlar(true,"observador","operador",true))
        assertFalse(Permisos.puedeControlar(true,"operador","observador",true))
        assertFalse(Permisos.puedeControlar(true,"operador","operador",false))
        assertFalse(Permisos.puedeControlar(true,"","operador",true))
    }
    @Test fun observerPinCannotGrantOperatorEvenIfClientClaimsIt() {
        assertEquals("observador",Permisos.accesoEfectivo("observador","operador"))
        assertEquals("observador",Permisos.accesoEfectivo("operador","observador"))
        assertEquals("operador",Permisos.accesoEfectivo("operador","operador"))
    }
    @Test fun replayReflectionAndPreviousConnectionFramesAreRejected() {
        val c = ProtocoloSesion("sessionA",false)
        val s = ProtocoloSesion("sessionA",true)
        val frame = c.empaquetar(Mensaje("CMD","dispense","30"))
        assertNull(c.recibir(frame))
        assertNull(ProtocoloSesion("sessionB",true).recibir(frame))
        assertEquals(Mensaje("CMD","dispense","30"),s.recibir(frame))
        assertNull(s.recibir(frame))
        assertEquals(Mensaje("LECTURA","peso","50"),c.recibir(s.empaquetar(Mensaje("LECTURA","peso","50"))))
    }
    @Test fun malformedFramesCannotAdvanceCounter() {
        val s = ProtocoloSesion("session",true)
        assertNull(s.recibir("V2;session;C;999;bad"))
        assertEquals(Mensaje("CMD","pump","OFF"),s.recibir("V2;session;C;1;CMD;pump;OFF"))
    }
    @Test fun gcmRejectsWrongPinAndModifiedCiphertext() {
        val key = Cripto.deriveAESKey("234567")
        val encoded = Cripto.encrypt("CMD;dispense;30",key)
        assertEquals("CMD;dispense;30",Cripto.decrypt(encoded,key))
        assertNull(Cripto.decrypt(encoded,Cripto.deriveAESKey("765432")))
        val bytes = Base64.getDecoder().decode(encoded)
        bytes[15] = (bytes[15].toInt() xor 1).toByte()
        assertNull(Cripto.decrypt(Base64.getEncoder().encodeToString(bytes),key))
        assertNull(Cripto.decrypt("AA==",key))
    }
    @Test fun passwordHashesAreSaltedAndRejectWrongPassword() {
        val a = Cripto.hashPassword("segura123")
        val b = Cripto.hashPassword("segura123")
        assertFalse(a.first.contentEquals(b.first))
        assertTrue(Cripto.verifyPassword("segura123",a.first,a.second))
        assertFalse(Cripto.verifyPassword("incorrecta",a.first,a.second))
    }
}
