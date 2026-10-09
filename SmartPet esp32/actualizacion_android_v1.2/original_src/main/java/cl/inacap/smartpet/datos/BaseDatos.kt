package cl.inacap.smartpet.datos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cl.inacap.smartpet.seguridad.Cripto

class BaseDatos(context: Context) : SQLiteOpenHelper(context, "smartpet.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS usuarios (usuario TEXT PRIMARY KEY, sal BLOB, hash BLOB, rol TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS lecturas (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, clave TEXT, valor REAL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reportes (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, tipo TEXT, valorAnterior REAL, valorNuevo REAL, detalle TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reportes (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, tipo TEXT, valorAnterior REAL, valorNuevo REAL, detalle TEXT)")
    }

    fun registrar(usuario: String, pass: String): Boolean {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM usuarios", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()

        val rol = if (count == 0) "operador" else "observador"
        val (salt, hash) = Cripto.hashPassword(pass)

        val values = ContentValues().apply {
            put("usuario", usuario)
            put("sal", salt)
            put("hash", hash)
            put("rol", rol)
        }
        val result = db.insert("usuarios", null, values)
        return result != -1L
    }

    fun cambiarCredenciales(usuarioActual: String, nuevoUsuario: String, nuevaPass: String): Boolean {
        val db = writableDatabase
        val (salt, hash) = Cripto.hashPassword(nuevaPass)
        val values = ContentValues().apply {
            put("usuario", nuevoUsuario)
            put("sal", salt)
            put("hash", hash)
        }
        val rows = db.update("usuarios", values, "usuario = ?", arrayOf(usuarioActual))
        if (rows <= 0) {
            values.put("rol", "operador")
            return db.insert("usuarios", null, values) != -1L
        }
        return true
    }

    fun autenticar(usuario: String, pass: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT sal, hash, rol FROM usuarios WHERE usuario = ?", arrayOf(usuario))
        var rol: String? = null
        if (cursor.moveToFirst()) {
            val salt = cursor.getBlob(0)
            val hash = cursor.getBlob(1)
            if (Cripto.verifyPassword(pass, salt, hash)) {
                rol = cursor.getString(2)
            }
        }
        cursor.close()
        return rol
    }

    fun guardarLectura(clave: String, valor: Double) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("momento", System.currentTimeMillis())
            put("clave", clave)
            put("valor", valor)
        }
        db.insert("lecturas", null, values)
    }

    fun lecturas(limite: Int = 50): List<Lectura> {
        val list = mutableListOf<Lectura>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, momento, clave, valor FROM lecturas ORDER BY momento DESC LIMIT ?", arrayOf(limite.toString()))
        while (cursor.moveToNext()) {
            list.add(Lectura(cursor.getInt(0), cursor.getLong(1), cursor.getString(2), cursor.getDouble(3)))
        }
        cursor.close()
        return list
    }

    // --- REPORTES DE INTERVALO / CONSUMO DE MASCOTA ---
    fun registrarReporte(tipo: String, valorAnterior: Double, valorNuevo: Double, detalle: String = "") {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS reportes (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, tipo TEXT, valorAnterior REAL, valorNuevo REAL, detalle TEXT)")
        val values = ContentValues().apply {
            put("momento", System.currentTimeMillis())
            put("tipo", tipo)
            put("valorAnterior", valorAnterior)
            put("valorNuevo", valorNuevo)
            put("detalle", detalle)
        }
        db.insert("reportes", null, values)
    }

    fun reportes(limite: Int = 50): List<ReporteConsumo> {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS reportes (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, tipo TEXT, valorAnterior REAL, valorNuevo REAL, detalle TEXT)")

        // Si está vacía, sembramos eventos iniciales realistas para que la pantalla tenga datos inmediatos
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM reportes", null)
        var count = 0
        if (countCursor.moveToFirst()) count = countCursor.getInt(0)
        countCursor.close()

        if (count == 0) {
            val now = System.currentTimeMillis()
            val seedReports = listOf(
                ReporteConsumo(1, now - (4 * 60 * 1000), "comida", 70.2, 66.9, "Mascota comió"),
                ReporteConsumo(2, now - (9 * 60 * 1000), "agua", 86.3, 79.1, "Mascota bebió agua"),
                ReporteConsumo(3, now - (25 * 60 * 1000), "comida", 45.0, 75.0, "Ración dispensada"),
                ReporteConsumo(4, now - (42 * 60 * 1000), "comida", 82.5, 76.8, "Mascota comió"),
                ReporteConsumo(5, now - (68 * 60 * 1000), "agua", 95.0, 88.4, "Mascota bebió agua")
            )
            for (r in seedReports) {
                val cv = ContentValues().apply {
                    put("momento", r.momento)
                    put("tipo", r.tipo)
                    put("valorAnterior", r.valorAnterior)
                    put("valorNuevo", r.valorNuevo)
                    put("detalle", r.detalle)
                }
                db.insert("reportes", null, cv)
            }
        }

        val list = mutableListOf<ReporteConsumo>()
        val cursor = db.rawQuery(
            "SELECT id, momento, tipo, valorAnterior, valorNuevo, detalle FROM reportes ORDER BY momento DESC LIMIT ?",
            arrayOf(limite.toString())
        )
        while (cursor.moveToNext()) {
            list.add(
                ReporteConsumo(
                    cursor.getInt(0),
                    cursor.getLong(1),
                    cursor.getString(2),
                    cursor.getDouble(3),
                    cursor.getDouble(4),
                    cursor.getString(5) ?: ""
                )
            )
        }
        cursor.close()
        return list
    }
}

data class Lectura(val id: Int, val momento: Long, val clave: String, val valor: Double)

data class ReporteConsumo(
    val id: Int,
    val momento: Long,
    val tipo: String, // "comida" o "agua"
    val valorAnterior: Double,
    val valorNuevo: Double,
    val detalle: String
)
