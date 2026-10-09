package cl.inacap.smartpet.datos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cl.inacap.smartpet.seguridad.Cripto

class BaseDatos(context: Context) : SQLiteOpenHelper(context, "smartpet.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE usuarios (usuario TEXT PRIMARY KEY, sal BLOB NOT NULL, hash BLOB NOT NULL, rol TEXT NOT NULL)")
        db.execSQL("CREATE TABLE lecturas (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, clave TEXT, valor REAL)")
        db.execSQL("CREATE TABLE reportes (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, tipo TEXT, valorAnterior REAL, valorNuevo REAL, detalle TEXT, origen TEXT NOT NULL DEFAULT 'SIMULACION')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS reportes (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, tipo TEXT, valorAnterior REAL, valorNuevo REAL, detalle TEXT)")
        if (oldVersion < 3) db.execSQL("ALTER TABLE reportes ADD COLUMN origen TEXT NOT NULL DEFAULT 'LEGACY'")
    }
    fun registrar(usuario: String, pass: String): Boolean {
        if (usuario.isBlank() || usuario.length > 40 || pass.length < 8) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val count = db.rawQuery("SELECT COUNT(*) FROM usuarios", null).use { it.moveToFirst(); it.getInt(0) }
            val (salt, hash) = Cripto.hashPassword(pass)
            val values = ContentValues().apply { put("usuario", usuario.trim()); put("sal", salt); put("hash", hash); put("rol", if (count == 0) "operador" else "observador") }
            val ok = db.insertWithOnConflict("usuarios", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            if (ok) db.setTransactionSuccessful()
            ok
        } finally { db.endTransaction() }
    }
    fun autenticar(usuario: String, pass: String): String? {
        return readableDatabase.rawQuery("SELECT sal, hash, rol FROM usuarios WHERE usuario = ?", arrayOf(usuario)).use { cursor ->
            if (cursor.moveToFirst() && Cripto.verifyPassword(pass, cursor.getBlob(0), cursor.getBlob(1))) {
                cursor.getString(2).takeIf { it in listOf("operador", "observador") }
            } else null
        }
    }
    fun cambiarCredenciales(usuarioActual: String, nuevoUsuario: String, nuevaPass: String, passActual: String): Boolean {
        if (nuevoUsuario.isBlank() || nuevoUsuario.length > 40 || nuevaPass.length < 8) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            if (autenticar(usuarioActual, passActual) == null) return false
            val (salt, hash) = Cripto.hashPassword(nuevaPass)
            val values = ContentValues().apply { put("usuario", nuevoUsuario.trim()); put("sal", salt); put("hash", hash) }
            val ok = db.updateWithOnConflict("usuarios", values, "usuario = ?", arrayOf(usuarioActual), SQLiteDatabase.CONFLICT_IGNORE) == 1
            if (ok) db.setTransactionSuccessful()
            ok
        } finally { db.endTransaction() }
    }
    fun guardarLectura(clave: String, valor: Double) {
        writableDatabase.insert("lecturas", null, ContentValues().apply { put("momento", System.currentTimeMillis()); put("clave", clave); put("valor", valor) })
    }
    fun lecturas(limite: Int = 50): List<Lectura> {
        val list = mutableListOf<Lectura>()
        readableDatabase.rawQuery("SELECT id,momento,clave,valor FROM lecturas ORDER BY momento DESC LIMIT ?", arrayOf(limite.toString())).use {
            while (it.moveToNext()) list.add(Lectura(it.getInt(0), it.getLong(1), it.getString(2), it.getDouble(3)))
        }
        return list
    }
    fun registrarReporte(tipo: String, valorAnterior: Double, valorNuevo: Double, detalle: String = "") {
        writableDatabase.insert("reportes", null, ContentValues().apply {
            put("momento", System.currentTimeMillis()); put("tipo", tipo); put("valorAnterior", valorAnterior)
            put("valorNuevo", valorNuevo); put("detalle", detalle); put("origen", "SIMULACION")
        })
    }
    fun reportes(limite: Int = 50): List<ReporteConsumo> {
        val list = mutableListOf<ReporteConsumo>()
        readableDatabase.rawQuery("SELECT id,momento,tipo,valorAnterior,valorNuevo,detalle,origen FROM reportes ORDER BY momento DESC LIMIT ?", arrayOf(limite.toString())).use {
            while (it.moveToNext()) list.add(ReporteConsumo(it.getInt(0), it.getLong(1), it.getString(2), it.getDouble(3), it.getDouble(4),
                (it.getString(5) ?: "") + if (it.getString(6) == "LEGACY") " · Registro anterior sin verificación" else " · Simulación Android"))
        }
        return list
    }
}
data class Lectura(val id: Int, val momento: Long, val clave: String, val valor: Double)
data class ReporteConsumo(val id: Int, val momento: Long, val tipo: String, val valorAnterior: Double, val valorNuevo: Double, val detalle: String)
