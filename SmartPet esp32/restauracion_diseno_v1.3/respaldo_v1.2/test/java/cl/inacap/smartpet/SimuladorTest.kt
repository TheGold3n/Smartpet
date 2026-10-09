package cl.inacap.smartpet

import org.junit.Assert.*
import org.junit.Test

class SimuladorTest {
    @Test fun observerCannotChangeAnyActuatorOrWater() {
        val s = Simulador()
        val original = s.actual()
        for ((cmd,value) in listOf("dispense" to "30", "pump" to "ON", "refill_water" to "ON", "emergency_stop" to "ON")) {
            assertFalse(s.ejecutar(cmd,value,false).aceptado)
            assertEquals(original,s.actual())
        }
    }
    @Test fun portionIsAddedOnlyOnceAfterCompletion() {
        val s = Simulador()
        assertTrue(s.ejecutar("dispense","30",true).aceptado)
        assertTrue(s.actual().dispensador)
        assertEquals(50.0,s.actual().peso,0.0)
        assertFalse(s.ejecutar("dispense","30",true).aceptado)
        s.completarDispensado()
        s.completarDispensado()
        assertEquals(80.0,s.actual().peso,0.0)
        assertFalse(s.actual().dispensador)
    }
    @Test fun invalidOrExcessivePortionsAreRejected() {
        val s = Simulador()
        for (v in listOf("0","-1","61","NaN","Infinity","texto")) assertFalse(s.ejecutar("dispense",v,true).aceptado)
        s.ajustarPeso(90.0)
        assertFalse(s.ejecutar("dispense","30",true).aceptado)
        assertEquals(90.0,s.actual().peso,0.0)
    }
    @Test fun pumpDoesNotRefillAndStopsWhenWaterDrops() {
        val s = Simulador()
        assertTrue(s.ejecutar("pump","ON",true).aceptado)
        assertEquals(90.0,s.actual().agua,0.0)
        s.ajustarAgua(19.0)
        assertFalse(s.actual().bomba)
        assertFalse(s.ejecutar("pump","ON",true).aceptado)
        assertTrue(s.ejecutar("refill_water","ON",true).aceptado)
        assertEquals(95.0,s.actual().agua,0.0)
        assertFalse(s.actual().bomba)
    }
    @Test fun disconnectCancelsPendingFoodAndPump() {
        val s = Simulador()
        s.ejecutar("pump","ON",true)
        s.ejecutar("dispense","30",true)
        s.detener(); s.completarDispensado()
        assertFalse(s.actual().bomba)
        assertFalse(s.actual().dispensador)
        assertEquals(50.0,s.actual().peso,0.0)
    }
    @Test fun unknownCommandDoesNotMutateState() {
        val s = Simulador()
        assertFalse(s.ejecutar("unknown","ON",true).aceptado)
        assertEquals(EstadoSimulador(),s.actual())
    }
    @Test fun repeatedPumpOnIsRejectedWhileAlreadyRunning() {
        val s = Simulador()
        assertTrue(s.ejecutar("pump", "ON", true).aceptado)
        assertFalse(s.ejecutar("pump", "ON", true).aceptado)
        assertTrue(s.actual().bomba)
        assertTrue(s.ejecutar("pump", "OFF", true).aceptado)
        assertTrue(s.ejecutar("pump", "ON", true).aceptado)
    }
}
