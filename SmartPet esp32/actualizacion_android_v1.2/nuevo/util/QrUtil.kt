package cl.inacap.smartpet.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrUtil {
    fun generarQrBitmap(contenido: String, size: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    fun ipValida(ip: String): Boolean {
        val partes = ip.split('.')
        return partes.size == 4 && partes.all { it.matches(Regex("[0-9]{1,3}")) && it.toInt() in 0..255 } && ip != "127.0.0.1" && ip != "0.0.0.0"
    }
    data class DatosEstacion(val pin: String, val ip: String)
    fun parsearQr(contenido: String): DatosEstacion? {
        val partes = contenido.trim().split(':')
        if (partes.size != 3 || partes[0] != "SMARTPET" || !partes[1].matches(Regex("[0-9]{6}")) || !ipValida(partes[2])) return null
        return DatosEstacion(partes[1], partes[2])
    }
}
