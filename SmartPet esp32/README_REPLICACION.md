# 🐾 SmartPet Station - Guía de Replicación e Instalación en Cualquier PC (Modo 100% Wi-Fi Standalone)

Esta guía explica cómo instalar y ejecutar el proyecto **SmartPet Station** en **cualquier otra computadora y teléfono móvil**, sin necesidad de Android Studio, sin cables y **sin depuración por USB**.

---

## 📦 Archivos Necesarios

En esta carpeta (o en el Escritorio) tienes todo lo necesario:

1. **`SmartPet_App.apk`**: Instalador de la aplicación Android listo para cualquier celular.
2. **`simulator_server.py`**: Servidor Hub en Python que simula los sensores (Tolva de comida y Estanque con recirculación) y actuadores del ESP32.
3. **`iniciar_servidor.bat`** (o `INICIAR_SMARTPET_HUB.bat`): Script de inicio en 1 solo clic.

---

## 🚀 Paso 1: Configurar la Computadora (Servidor Hub ESP32)

1. **Instalar Python:**  
   * Si la PC no tiene Python, descárgalo desde [python.org](https://www.python.org/downloads/).  
   * ⚠️ **MUY IMPORTANTE:** Durante la instalación, marca la casilla **`Add Python to PATH`** (Agregar Python al PATH).

2. **Iniciar el Servidor Simulador:**  
   * Haz doble clic sobre el archivo **`iniciar_servidor.bat`** (o ejecuta `python simulator_server.py` en la terminal).  
   * El script instalará automáticamente la librería criptográfica `cryptography` si no la tiene.  
   * **¿Qué ocurrirá automáticamente?**  
     1. La consola detectará la dirección IP local de tu PC en la red Wi-Fi (ejemplo: `192.168.1.50`).  
     2. Se abrirá automáticamente tu navegador web mostrando una tarjeta con el **Código QR Único de Vinculación**.  
     3. El servidor quedará a la espera de la conexión del celular en el puerto `5050`.  
   * *(Si Windows muestra una alerta del Firewall, selecciona **"Permitir acceso en redes privadas"**).*

---

## 📱 Paso 2: Configurar el Teléfono Móvil

1. **Conexión a la Misma Red:**  
   * Conecta el teléfono móvil a la **misma red Wi-Fi** donde está la computadora.  
   * *(Opcional): Si estás en un lugar sin router (universidad o red pública con restricciones), enciende la **Zona Wi-Fi / Hotspot del celular** y conecta la PC a esa red.*

2. **Instalar la Aplicación:**  
   * Envía el archivo **`SmartPet_App.apk`** al celular (por WhatsApp Web, Telegram, Google Drive, Bluetooth o cable).  
   * En el celular, pulsa sobre el archivo `.apk` para instalarlo.  
   * Si Android pregunta, concede permiso para *"Instalar aplicaciones de fuentes desconocidas"*.

---

## 🔗 Paso 3: Vinculación Instantánea por Código QR

1. Abre la aplicación **SmartPet Station** en el teléfono.  
2. Inicia sesión con las credenciales por defecto (o cámbialas con el botón *"Cambiar Usuario y Contraseña"*):  
   * **Usuario:** `admin`  
   * **Contraseña:** `admin123`  
3. En la pantalla **Vincular Estación**, presiona el botón:  
   👉 **`[ 📷 Escanear Código QR de Estación ]`**  
4. El teléfono te pedirá permiso para usar la cámara **una única vez**. Pulsa **Permitir**.  
5. **Apunta la cámara del celular a la pantalla de la PC**, donde está el código QR generado por el servidor.  
6. **¡Listo!** La app leerá la IP y el PIN al instante y se conectará de inmediato a la estación.

---

## 🐾 Funciones Listas para Demostración

* **Comida en Tolva:** Muestra gramos y porcentaje en tiempo real. Presiona **`[ Dispensar Ración (+30g) ]`** y verás cómo el nivel sube inmediatamente y el servidor registra la acción.
* **Estanque con Recirculación Continua:** Sensores integrados en el estanque para mantener agua fresca y en movimiento. Si el agua llega a 0%, se detiene en 0% esperando recambio físico.
* **Relleno Manual:** Presiona **`[ 🫗 Rellené el Estanque a Mano (+95%) ]`**; el sensor detectará la subida repentina y registrará: *"Estanque rellenado manualmente: pasó de X% a 95%"*.
* **Historial de Consumo:** Registra eventos reales con intervalos (*"pasó de X a Y"*).
* **Alertas Interactivas con la Voz de la Mascota:** Notificaciones con botones de acción directa en la barra de Android.
* **Preparar Horarios:** Horarios autónomos almacenados en el módulo RTC DS3231 del hub.
