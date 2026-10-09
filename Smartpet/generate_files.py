import os

base_dir = r"c:\Users\Tokyotech\Desktop\Proyectos\Proyectos-android-studio\Smartpet\app\src\main\java\cl\inacap\smartpet"
os.makedirs(os.path.join(base_dir, "seguridad"), exist_ok=True)
os.makedirs(os.path.join(base_dir, "enlace"), exist_ok=True)
os.makedirs(os.path.join(base_dir, "datos"), exist_ok=True)
os.makedirs(os.path.join(base_dir, "ui"), exist_ok=True)

manifest_path = r"c:\Users\Tokyotech\Desktop\Proyectos\Proyectos-android-studio\Smartpet\app\src\main\AndroidManifest.xml"
manifest_content = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="cl.inacap.smartpet">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:usesCleartextTraffic="false"
        android:theme="@style/Theme.SmartPetStation">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.SmartPetStation"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
"""
with open(manifest_path, "w", encoding="utf-8") as f:
    f.write(manifest_content)

cripto_content = """package cl.inacap.smartpet.seguridad

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Cripto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    fun hashPassword(password: String): Pair<ByteArray, ByteArray> {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val spec = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Pair(salt, hash)
    }

    fun verifyPassword(password: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return MessageDigest.isEqual(hash, expectedHash)
    }

    fun deriveAESKey(pin: String): SecretKeySpec {
        val defaultSalt = "SmartPetSalt_123".toByteArray()
        val spec = PBEKeySpec(pin.toCharArray(), defaultSalt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, secretKey: SecretKeySpec): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherText = cipher.doFinal(plainText.toByteArray())
        val combined = iv + cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherTextBase64: String, secretKey: SecretKeySpec): String? {
        return try {
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val cipherText = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(cipherText))
        } catch (e: Exception) {
            null
        }
    }
}
"""
with open(os.path.join(base_dir, "seguridad", "Cripto.kt"), "w", encoding="utf-8") as f:
    f.write(cripto_content)

enlace_content = """package cl.inacap.smartpet.enlace

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class Mensaje(val tipo: String, val clave: String, val valor: String) {
    companion object {
        fun parse(line: String): Mensaje? {
            val parts = line.split(";")
            if (parts.size == 3) {
                return Mensaje(parts[0], parts[1], parts[2])
            }
            return null
        }
    }
    
    override fun toString(): String {
        return "$tipo;$clave;$valor"
    }
}

interface Enlace {
    val estado: StateFlow<String>
    val entrantes: SharedFlow<Mensaje>
    suspend fun enviar(mensaje: Mensaje)
    fun cerrar()
}
"""
with open(os.path.join(base_dir, "enlace", "Enlace.kt"), "w", encoding="utf-8") as f:
    f.write(enlace_content)

enlacebase_content = """package cl.inacap.smartpet.enlace

import cl.inacap.smartpet.seguridad.Cripto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import javax.crypto.spec.SecretKeySpec

abstract class EnlaceBase(pin: String) : Enlace {
    protected val _estado = MutableStateFlow("Desconectado")
    override val estado = _estado.asStateFlow()

    protected val _entrantes = MutableSharedFlow<Mensaje>()
    override val entrantes = _entrantes.asSharedFlow()

    protected var writer: PrintWriter? = null
    protected var reader: BufferedReader? = null
    protected var socketJob: Job? = null
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private val key: SecretKeySpec = Cripto.deriveAESKey(pin)

    protected fun startReading() {
        socketJob = scope.launch {
            try {
                _estado.value = "Conectado"
                while (isActive) {
                    val encryptedLine = reader?.readLine() ?: break
                    val decryptedLine = Cripto.decrypt(encryptedLine, key)
                    if (decryptedLine != null) {
                        val msg = Mensaje.parse(decryptedLine)
                        if (msg != null) {
                            _entrantes.emit(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _estado.value = "Desconectado"
                cerrar()
            }
        }
    }

    override suspend fun enviar(mensaje: Mensaje) {
        withContext(Dispatchers.IO) {
            try {
                val encrypted = Cripto.encrypt(mensaje.toString(), key)
                writer?.println(encrypted)
                writer?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
"""
with open(os.path.join(base_dir, "enlace", "EnlaceBase.kt"), "w", encoding="utf-8") as f:
    f.write(enlacebase_content)

bluetooth_content = """package cl.inacap.smartpet.enlace

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothEnlace(private val pin: String, isServer: Boolean, device: BluetoothDevice? = null, adapter: BluetoothAdapter? = null) : EnlaceBase(pin) {
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isServer && adapter != null) {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord("SmartPet", uuid)
                    socket = serverSocket?.accept()
                    serverSocket?.close()
                } else if (!isServer && device != null) {
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket?.connect()
                }
                
                socket?.let {
                    reader = BufferedReader(InputStreamReader(it.inputStream))
                    writer = PrintWriter(OutputStreamWriter(it.outputStream))
                    startReading()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _estado.value = "Error"
                cerrar()
            }
        }
    }

    override fun cerrar() {
        try {
            socketJob?.cancel()
            writer?.close()
            reader?.close()
            socket?.close()
            serverSocket?.close()
            _estado.value = "Desconectado"
        } catch (e: Exception) {}
    }
}
"""
with open(os.path.join(base_dir, "enlace", "BluetoothEnlace.kt"), "w", encoding="utf-8") as f:
    f.write(bluetooth_content)

wifi_content = """package cl.inacap.smartpet.enlace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class WifiEnlace(private val pin: String, isServer: Boolean, ip: String = "") : EnlaceBase(pin) {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isServer) {
                    serverSocket = ServerSocket(5050)
                    socket = serverSocket?.accept()
                } else {
                    socket = Socket()
                    socket?.connect(InetSocketAddress(ip, 5050), 5000)
                }
                
                socket?.let {
                    reader = BufferedReader(InputStreamReader(it.getInputStream()))
                    writer = PrintWriter(OutputStreamWriter(it.getOutputStream()))
                    startReading()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _estado.value = "Error"
                cerrar()
            }
        }
    }

    override fun cerrar() {
        try {
            socketJob?.cancel()
            writer?.close()
            reader?.close()
            socket?.close()
            serverSocket?.close()
            _estado.value = "Desconectado"
        } catch (e: Exception) {}
    }
}
"""
with open(os.path.join(base_dir, "enlace", "WifiEnlace.kt"), "w", encoding="utf-8") as f:
    f.write(wifi_content)

notificaciones_content = """package cl.inacap.smartpet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notificaciones {
    private const val CHANNEL_ID = "smartpet_alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas SmartPet"
            val descriptionText = "Notificaciones críticas de SmartPet"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun mostrar(context: Context, id: Int, titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
    }
}
"""
with open(os.path.join(base_dir, "Notificaciones.kt"), "w", encoding="utf-8") as f:
    f.write(notificaciones_content)

sesion_content = """package cl.inacap.smartpet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import cl.inacap.smartpet.enlace.Enlace

object Sesion {
    private val _nivelAlimento = MutableStateFlow<Double?>(null)
    val nivelAlimento = _nivelAlimento.asStateFlow()

    private val _dispensadorActivo = MutableStateFlow(false)
    val dispensadorActivo = _dispensadorActivo.asStateFlow()

    private val _bombaActiva = MutableStateFlow(false)
    val bombaActiva = _bombaActiva.asStateFlow()

    private val _rol = MutableStateFlow("")
    val rol = _rol.asStateFlow()

    private val _enlaceActivo = MutableStateFlow<Enlace?>(null)
    val enlaceActivo = _enlaceActivo.asStateFlow()
    
    val umbralCritico = 20.0

    fun actualizarNivel(nivel: Double?) {
        _nivelAlimento.value = nivel
    }

    fun setDispensadorActivo(activo: Boolean) {
        _dispensadorActivo.value = activo
    }

    fun setBombaActiva(activa: Boolean) {
        _bombaActiva.value = activa
    }

    fun setRol(nuevoRol: String) {
        _rol.value = nuevoRol
    }

    fun setEnlace(enlace: Enlace?) {
        _enlaceActivo.value = enlace
    }
}
"""
with open(os.path.join(base_dir, "Sesion.kt"), "w", encoding="utf-8") as f:
    f.write(sesion_content)

basedatos_content = """package cl.inacap.smartpet.datos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cl.inacap.smartpet.seguridad.Cripto

class BaseDatos(context: Context) : SQLiteOpenHelper(context, "smartpet.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE usuarios (usuario TEXT PRIMARY KEY, sal BLOB, hash BLOB, rol TEXT)")
        db.execSQL("CREATE TABLE lecturas (id INTEGER PRIMARY KEY AUTOINCREMENT, momento INTEGER, clave TEXT, valor REAL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS lecturas")
        onCreate(db)
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
}

data class Lectura(val id: Int, val momento: Long, val clave: String, val valor: Double)
"""
with open(os.path.join(base_dir, "datos", "BaseDatos.kt"), "w", encoding="utf-8") as f:
    f.write(basedatos_content)

ui_login_content = """package cl.inacap.smartpet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.datos.BaseDatos
import kotlinx.coroutines.delay

@Composable
fun PantallaLogin(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BaseDatos(context) }
    var usuario by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    
    var intentos by remember { mutableStateOf(0) }
    var bloqueadoHasta by remember { mutableStateOf(0L) }
    
    LaunchedEffect(bloqueadoHasta) {
        if (bloqueadoHasta > 0) {
            while (System.currentTimeMillis() < bloqueadoHasta) {
                delay(1000)
            }
            intentos = 0
            bloqueadoHasta = 0
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (bloqueadoHasta > System.currentTimeMillis()) {
            Text("Bloqueado por intentos fallidos", color = MaterialTheme.colorScheme.error)
        } else {
            Row {
                Button(onClick = {
                    if (pass.length < 8) {
                        mensaje = "La contraseña debe tener al menos 8 caracteres"
                        return@Button
                    }
                    val rol = db.autenticar(usuario, pass)
                    if (rol != null) {
                        Sesion.setRol(rol)
                        onLoginSuccess()
                    } else {
                        intentos++
                        mensaje = "Credenciales incorrectas"
                        if (intentos >= 5) {
                            bloqueadoHasta = System.currentTimeMillis() + 30000
                        }
                    }
                }) {
                    Text("Ingresar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (pass.length < 8) {
                        mensaje = "La contraseña debe tener al menos 8 caracteres"
                        return@Button
                    }
                    if (db.registrar(usuario, pass)) {
                        mensaje = "Registrado correctamente. Ahora puede ingresar."
                    } else {
                        mensaje = "Error al registrar"
                    }
                }) {
                    Text("Registrar")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(mensaje, color = MaterialTheme.colorScheme.error)
    }
}
"""
with open(os.path.join(base_dir, "ui", "PantallaLogin.kt"), "w", encoding="utf-8") as f:
    f.write(ui_login_content)

ui_conexion_content = """package cl.inacap.smartpet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.enlace.BluetoothEnlace
import cl.inacap.smartpet.enlace.WifiEnlace
import cl.inacap.smartpet.Sesion

@Composable
fun PantallaConexion(onConnectedNodo: () -> Unit, onConnectedPanel: () -> Unit) {
    var isNodo by remember { mutableStateOf(false) }
    var isWifi by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Configuración de Conexión", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row {
            RadioButton(selected = isNodo, onClick = { isNodo = true })
            Text("Nodo IoT (Simulador)")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = !isNodo, onClick = { isNodo = false })
            Text("Panel de Control")
        }
        
        Row {
            RadioButton(selected = isWifi, onClick = { isWifi = true })
            Text("Wi-Fi")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = !isWifi, onClick = { isWifi = false })
            Text("Bluetooth")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it },
            label = { Text("PIN (6 dígitos)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (isWifi && !isNodo) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP del Nodo") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (pin.length != 6) return@Button
            
            val enlace = if (isWifi) {
                WifiEnlace(pin, isNodo, ip)
            } else {
                BluetoothEnlace(pin, isNodo, null, android.bluetooth.BluetoothAdapter.getDefaultAdapter())
            }
            Sesion.setEnlace(enlace)
            
            if (isNodo) onConnectedNodo() else onConnectedPanel()
        }) {
            Text("Conectar")
        }
    }
}
"""
with open(os.path.join(base_dir, "ui", "PantallaConexion.kt"), "w", encoding="utf-8") as f:
    f.write(ui_conexion_content)

ui_nodo_content = """package cl.inacap.smartpet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Notificaciones
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.enlace.Mensaje
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PantallaNodo(onDisconnect: () -> Unit) {
    val context = LocalContext.current
    var peso by remember { mutableStateOf(50f) }
    val dispensador by Sesion.dispensadorActivo.collectAsState()
    val bomba by Sesion.bombaActiva.collectAsState()
    val enlace = Sesion.enlaceActivo.collectAsState().value
    val estado by (enlace?.estado ?: MutableStateFlow("Desconectado")).collectAsState()

    LaunchedEffect(estado) {
        if (estado == "Desconectado") {
            Sesion.setDispensadorActivo(false)
            Sesion.setBombaActiva(false)
            Notificaciones.mostrar(context, 1, "Alerta", "Enlace perdido. Estado Seguro activado.")
        }
    }

    LaunchedEffect(enlace) {
        enlace?.entrantes?.collectLatest { msg ->
            if (msg.tipo == "CMD") {
                when (msg.clave) {
                    "dispense" -> Sesion.setDispensadorActivo(msg.valor == "ON")
                    "pump" -> Sesion.setBombaActiva(msg.valor == "ON")
                }
                enlace.enviar(Mensaje("ACK", msg.clave, msg.valor))
            }
        }
    }

    LaunchedEffect(peso) {
        while (true) {
            if (estado == "Conectado") {
                enlace?.enviar(Mensaje("LECTURA", "peso", peso.toString()))
            }
            delay(2000)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Nodo Simulador", style = MaterialTheme.typography.headlineMedium)
        Text("Estado: $estado")
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Peso/Nivel de alimento: ${"%.1f".format(peso)} g")
        Slider(
            value = peso,
            onValueChange = { peso = it },
            valueRange = 0f..100f
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Actuadores:")
        Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}")
        Text("Bomba de agua: ${if (bomba) "ENCENDIDA" else "APAGADA"}")
        
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { 
            enlace?.cerrar()
            onDisconnect()
        }) {
            Text("Desconectar")
        }
    }
}
"""
with open(os.path.join(base_dir, "ui", "PantallaNodo.kt"), "w", encoding="utf-8") as f:
    f.write(ui_nodo_content)

ui_panel_content = """package cl.inacap.smartpet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.Notificaciones
import cl.inacap.smartpet.Sesion
import cl.inacap.smartpet.datos.BaseDatos
import cl.inacap.smartpet.enlace.Mensaje
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PantallaPanel(onDisconnect: () -> Unit, onHistorial: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BaseDatos(context) }
    val scope = rememberCoroutineScope()
    
    val enlace = Sesion.enlaceActivo.collectAsState().value
    val estado by (enlace?.estado ?: MutableStateFlow("Desconectado")).collectAsState()
    val rol by Sesion.rol.collectAsState()
    val nivel by Sesion.nivelAlimento.collectAsState()

    var alertaMostrada by remember { mutableStateOf(false) }

    LaunchedEffect(enlace) {
        enlace?.entrantes?.collectLatest { msg ->
            if (msg.tipo == "LECTURA" && msg.clave == "peso") {
                val valor = msg.valor.toDoubleOrNull()
                if (valor != null) {
                    Sesion.actualizarNivel(valor)
                    db.guardarLectura("peso", valor)
                    
                    if (valor < Sesion.umbralCritico && !alertaMostrada) {
                        Notificaciones.mostrar(context, 2, "Nivel Bajo", "Nivel de alimento: $valor g")
                        alertaMostrada = true
                    } else if (valor >= Sesion.umbralCritico) {
                        alertaMostrada = false
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Panel de Control", style = MaterialTheme.typography.headlineMedium)
        Text("Rol: $rol")
        Text("Estado Enlace: $estado")
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Telemetría: ${nivel?.toString() ?: "Sin datos"} g", style = MaterialTheme.typography.bodyLarge)
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch { enlace?.enviar(Mensaje("CMD", "dispense", "ON")) }
            },
            enabled = rol == "operador"
        ) {
            Text("Dispensar ración")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch { enlace?.enviar(Mensaje("CMD", "pump", "ON")) }
            },
            enabled = rol == "operador"
        ) {
            Text("Bomba ON")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch { enlace?.enviar(Mensaje("CMD", "pump", "OFF")) }
            },
            enabled = rol == "operador"
        ) {
            Text("Bomba OFF")
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Row {
            Button(onClick = onHistorial) {
                Text("Ver Historial")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                enlace?.cerrar()
                onDisconnect()
            }) {
                Text("Desconectar")
            }
        }
    }
}
"""
with open(os.path.join(base_dir, "ui", "PantallaPanel.kt"), "w", encoding="utf-8") as f:
    f.write(ui_panel_content)

ui_historial_content = """package cl.inacap.smartpet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.smartpet.datos.BaseDatos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PantallaHistorial(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BaseDatos(context) }
    val lecturas = remember { db.lecturas(50) }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Historial de Lecturas", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lecturas) { lectura ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Fecha: ${dateFormat.format(Date(lectura.momento))}")
                        Text("${lectura.clave}: ${lectura.valor}")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Volver")
        }
    }
}
"""
with open(os.path.join(base_dir, "ui", "PantallaHistorial.kt"), "w", encoding="utf-8") as f:
    f.write(ui_historial_content)

main_activity_content = """package cl.inacap.smartpet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import cl.inacap.smartpet.ui.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Notificaciones.createChannel(this)

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var pantallaActual by rememberSaveable { mutableStateOf("LOGIN") }

                    when (pantallaActual) {
                        "LOGIN" -> PantallaLogin { pantallaActual = "CONEXION" }
                        "CONEXION" -> PantallaConexion(
                            onConnectedNodo = { pantallaActual = "NODO" },
                            onConnectedPanel = { pantallaActual = "PANEL" }
                        )
                        "NODO" -> PantallaNodo { pantallaActual = "CONEXION" }
                        "PANEL" -> PantallaPanel(
                            onDisconnect = { pantallaActual = "CONEXION" },
                            onHistorial = { pantallaActual = "HISTORIAL" }
                        )
                        "HISTORIAL" -> PantallaHistorial { pantallaActual = "PANEL" }
                    }
                }
            }
        }
    }
}
"""
with open(os.path.join(base_dir, "MainActivity.kt"), "w", encoding="utf-8") as f:
    f.write(main_activity_content)

print("Files generated successfully.")
