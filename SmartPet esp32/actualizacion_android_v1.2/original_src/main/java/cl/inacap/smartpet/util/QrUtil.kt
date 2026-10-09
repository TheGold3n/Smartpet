package cl.inacap.smartpet.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrUtil {
    fun generarQrBitmap(contenido: String, size: Int = 512): Bitmap {
        val bitMatrix = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(
                    x, 
                    y, 
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        return bmp
    }

    data class DatosEstacion(val pin: String, val ip: String)

    fun parsearQr(contenido: String): DatosEstacion? {
        val limpio = contenido.trim()
        // Formato SMARTPET:PIN:IP o SMARTPET;PIN;IP
        if (limpio.startsWith("SMARTPET", ignoreCase = true)) {
            val delimitador = if (';' in limpio) ';' else ':'
            val partes = limpio.split(delimitador)
            if (partes.size >= 3) {
                return DatosEstacion(pin = partes[1].trim(), ip = partes[2].trim())
            }
        }
        // Formato PIN@IP
        if ('@' in limpio) {
            val partes = limpio.split('@')
            if (partes.size == 2) {
                return DatosEstacion(pin = partes[0].trim(), ip = partes[1].trim())
            }
        }
        // Si contiene solo una IP válida
        if (limpio.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            return DatosEstacion(pin = "123456", ip = limpio)
        }
        // Si contiene 6 dígitos, es el PIN
        if (limpio.matches(Regex("^\\d{6}$"))) {
            return DatosEstacion(pin = limpio, ip = "127.0.0.1")
        }
        return null
    }
}
