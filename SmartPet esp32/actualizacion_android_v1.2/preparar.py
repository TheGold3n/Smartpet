from pathlib import Path
import difflib

root = Path(__file__).parent
backup = root / 'original_src'
source = backup / 'main/java/cl/inacap/smartpet'
stage = root / 'nuevo'
crypto = (source / 'seguridad/Cripto.kt').read_text(encoding='utf-8')
crypto = crypto.replace('import android.util.Base64', 'import java.util.Base64')
crypto = crypto.replace('Base64.encodeToString(combined, Base64.NO_WRAP)', 'Base64.getEncoder().encodeToString(combined)')
crypto = crypto.replace('Base64.decode(cipherTextBase64, Base64.NO_WRAP)', 'Base64.getDecoder().decode(cipherTextBase64)')
crypto = crypto.replace('val iv = combined.copyOfRange(0, IV_LENGTH)', 'require(combined.size >= IV_LENGTH + 16)\n            val iv = combined.copyOfRange(0, IV_LENGTH)')
(stage / 'seguridad/Cripto.kt').write_text(crypto, encoding='utf-8')
historial = (source / 'ui/PantallaHistorial.kt').read_text(encoding='utf-8')
historial = historial.replace('Historial de Consumo', 'Historial de Simulación').replace('Reporte por intervalos de actividad', 'Lecturas recibidas del nodo Android')
(stage / 'ui/PantallaHistorial.kt').write_text(historial, encoding='utf-8')
for file in ['ui/PantallaNodo.kt', 'ui/PantallaPanel.kt']:
    p = stage / file
    text = p.read_text(encoding='utf-8')
    text = text.replace('val estado by enlace!!.estado', 'val actual = enlace ?: return\n    val estado by actual.estado')
    text = text.replace('enlace!!.detalle', 'actual.detalle').replace('enlace!!.acceso', 'actual.acceso')
    p.write_text(text, encoding='utf-8')
p = stage / 'enlace/EnlaceBase.kt'
text = p.read_text(encoding='utf-8').replace('var ultimaRecepcion = System.nanoTime()', 'val ultimaRecepcion = java.util.concurrent.atomic.AtomicLong(System.nanoTime())')
text = text.replace('ultimaRecepcion = System.nanoTime()', 'ultimaRecepcion.set(System.nanoTime())')
text = text.replace('System.nanoTime() - ultimaRecepcion >', 'System.nanoTime() - ultimaRecepcion.get() >')
text = text.replace('while (isActive) { delay(2000); enviar(Mensaje("PING", "enlace", "OK")) }', '''while (isActive) {
                    delay(2000)
                    try { enviar(Mensaje("PING", "enlace", "OK")) }
                    catch (_: Exception) { cerrarSocket(); break }
                }''')
p.write_text(text, encoding='utf-8')
print('Fuentes preparadas en Kotlin.')
