# SmartPet Station — Aplicación Móvil Android para IoT (Unidad 2)

**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — INACAP  
**Proyecto:** SmartPet Station (Dispensador e Hidratador Automatizado de Mascotas)  
**Stack:** Android Studio | Kotlin DSL | Jetpack Compose (Material 3) | Bluetooth Clásico (RFCOMM/SPP) | Wi-Fi TCP | SQLite | Notificaciones Locales  
**Normativas de Seguridad:** ISO/IEC 27400:2022 & Lineamientos de Industria OT (IEC 62443)  

---

## 1. Descripción de la Problemática y Solución Multi-Nodo

El proyecto **SmartPet Station** aborda la necesidad de monitorear e interactuar a distancia con un dispensador e hidratador automatizado para mascotas. En la Unidad 2, la aplicación simula el nodo IoT mediante un dispositivo Android (**Nodo IoT / Teléfono A**) y permite su supervisión y control seguro desde un segundo equipo (**Panel de Control / Teléfono B**). En la Unidad 4, el Panel de Control se conectará directamente a la arquitectura física/simulada en Wokwi (compuesta por 3 microcontroladores ESP32).

---

## 2. Arquitectura del Software Android

El proyecto sigue una arquitectura en capas limpia, modular y orientada a eventos con `StateFlow` y `SharedFlow` sobre Corrutinas de Kotlin.

```
cl.inacap.smartpet
├── MainActivity.kt           <-- Control de Permisos en Tiempo de Ejecución y Navegación
├── Sesion.kt                 <-- Gestor de Estado Global (StateFlows, Fail-Safe OT, Alertas)
├── Notificaciones.kt         <-- Creación de Canal e Impresión de Notificaciones Locales (Android 13+)
│
├── seguridad
│   └── Cripto.kt             <-- PBKDF2-HMAC-SHA256, Cifrado Simétrico AES-256-GCM, Comparación Constant-Time
│
├── enlace
│   ├── Enlace.kt             <-- Interfaz Enlace y Data Class Mensaje (TIPO;CLAVE;VALOR)
│   ├── EnlaceBase.kt         <-- Corrutinas en Dispatchers.IO, Lectura/Escritura Cifrada
│   ├── BluetoothEnlace.kt    <-- Socket RFCOMM (UUID SPP 00001101-0000-1000-8000-00805F9B34FB)
│   └── WifiEnlace.kt         <-- Socket TCP (Puerto 5050) y Obtención de IP Local IPv4
│
├── datos
│   └── BaseDatos.kt          <-- SQLiteOpenHelper (Tablas: usuarios, lecturas), Control de Roles y Parametrización
│
└── ui
    ├── PantallaLogin.kt      <-- UI de Autenticación, Bloqueo tras 5 fallos (30s) y Hash PBKDF2
    ├── PantallaConexion.kt   <-- Selección de Rol, Medio (BT/Wi-Fi) y Derivación de Clave por PIN de 6 dígitos
    ├── PantallaNodo.kt       <-- Simulador de Tolva (Slider g/%), Actuadores y Envío Periódico (2s)
    ├── PantallaPanel.kt      <-- Telemetría en Vivo, Control de Actuadores (Operador) y Alertas de Nivel
    └── PantallaHistorial.kt  <-- LazyColumn con Historial de Lecturas (últimas 50)
```

---

## 3. Justificación de Tecnologías y Conectividad

| Elemento | Elección | Justificación Técnica |
| :--- | :--- | :--- |
| **Lenguaje e IDE** | Kotlin en Android Studio | Estándar oficial "Kotlin-first", manejo nativo de asincronía con Corrutinas y Flujos. |
| **Interfaz de Usuario** | Jetpack Compose + Material 3 | Declarativo, reactivo, libre de archivos XML superfluos y de alto rendimiento. |
| **Conexión Inalámbrica** | Bluetooth Clásico / Wi-Fi TCP | **Bluetooth Clásico (RFCOMM/SPP):** Ideal para corto alcance (~10m), independiente de redes externas.<br>**Wi-Fi TCP Local:** Para mayor distancia en red local (Hotspot/Router, puerto 5050). |
| **Base de Datos** | SQLite local (`SQLiteOpenHelper`) | Autónomo, funciona sin internet en plantas u hogares sin red externa, sin dependencias de terceros. |
| **Seguridad Criptográfica** | PBKDF2 + AES-256-GCM (`javax.crypto`) | Integrado en Android nativo. Cifrado autenticado de extremo a extremo que garantiza confidencialidad e integridad. |

---

## 4. Medidas de Seguridad Aplicadas (ISO/IEC 27400 y Lineamientos OT)

1. **Protección de Credenciales (Cifrado en Reposo):**
   - Las contraseñas de usuario nunca se almacenan en texto claro. Se procesan con **PBKDF2WithHmacSHA256** (120.000 iteraciones, sal aleatoria de 16 bytes y clave de 256 bits).
   - Validación de hashes mediante `MessageDigest.isEqual` para prevenir ataques de sincronización por tiempo (*timing attacks*).
2. **Cifrado de Enlace Simétrico (Cifrado en Tránsito):**
   - Todo paquete intercambiado vía Bluetooth o Wi-Fi se cifra dinámicamente con **AES-256-GCM**, derivando la clave simétrica a partir del código de vinculación de 6 dígitos mediante PBKDF2.
   - Incluye Vector de Inicialización (IV) de 12 bytes aleatorio por mensaje y etiqueta de autenticación GCM (128 bits).
3. **Control de Acceso basado en Roles (RBAC - Mínimo Privilegio):**
   - El primer usuario registrado obtiene el rol `operador` (puede enviar comandos al dispensador y bomba).
   - Los usuarios posteriores obtienen el rol `observador` (acceso exclusivo a telemetría en tiempo real e historial).
4. **Protección contra Inyección SQL:**
   - Consultas parametrizadas en SQLite (`rawQuery` con argumentos `?` y `ContentValues`).
5. **Estado Seguro y Resiliencia ante Fallas (Fail-Safe OT):**
   - Si la conexión inalámbrica se interrumpe, el **Nodo IoT** desactiva inmediatamente la bomba de agua, cierra la tolva del dispensador y emite una notificación local de "Enlace perdido".
6. **Protección contra Fuerza Bruta:**
   - Bloqueo de 30 segundos tras registrar 5 intentos fallidos consecutivos en el inicio de sesión.
7. **Endurecimiento de Aplicación (`AndroidManifest.xml`):**
   - `android:allowBackup="false"` para evitar la extracción de la base de datos local en respaldos.
   - `android:usesCleartextTraffic="false"` para forzar canales cifrados.

---

## 5. Matriz de Pruebas de Funcionamiento (P1 – P14)

| ID | Caso de Prueba | Pasos de Ejecución | Resultado Esperado | Estado |
| :---: | :--- | :--- | :--- | :---: |
| **P1** | Conexión BT/Wi-Fi | Nodo en modo espera; Panel ingresa PIN de 6 dígitos y se conecta. | Ambos dispositivos muestran estado "Conectado". | **APROBADO** |
| **P2** | Telemetría Periódica | Mover el Slider de nivel de alimento en el Nodo. | El Panel actualiza el peso en gramos en 2 segundos o menos. | **APROBADO** |
| **P3** | Alerta de Nivel Bajo | Ajustar Slider del Nodo por debajo de 20.0 g. | El Panel recibe y muestra una notificación local de alerta crítica. | **APROBADO** |
| **P4** | Envío de Comandos | Desde el Panel (Rol Operador), presionar "Dispensar ración" o "Bomba ON". | El Nodo cambia de estado visual y responde con una trama `ACK`. | **APROBADO** |
| **P5** | PIN de Vinculación Distinto | Nodo inicia con PIN `123456`, Panel intenta con `654321`. | Los mensajes no se pueden descifrar y son descartados automáticamente. | **APROBADO** |
| **P6** | Pérdida de Enlace (Fail-Safe) | Desactivar el Bluetooth o red Wi-Fi en el Nodo con la bomba activada. | El Nodo apaga actuadores (Estado Seguro) y emite notificación "Enlace perdido". | **APROBADO** |
| **P7** | Rechazo de Permisos | Denegar permisos de Bluetooth/Notificaciones al iniciar la app. | La aplicación maneja el estado sin cerrarse inesperadamente. | **APROBADO** |
| **P8** | Registro y Asignación de Roles | Registrar el primer usuario ("admin") y luego un segundo usuario ("user2"). | "admin" es asignado como `operador` y "user2" como `observador`. | **APROBADO** |
| **P9** | Bloqueo por Fuerza Bruta | Ingresar contraseña errónea 5 veces consecutivas en Login. | La app bloquea los intentos de inicio de sesión durante 30 segundos. | **APROBADO** |
| **P10** | Validación de Contraseña | Intentar registrar un usuario con clave de 5 caracteres. | Se rechaza por no cumplir el requisito mínimo de 8 caracteres. | **APROBADO** |
| **P11** | Restricción de Observador | Iniciar sesión como usuario `observador` y entrar al Panel. | Visualiza la telemetría pero los botones de comando se muestran deshabilitados. | **APROBADO** |
| **P12** | Almacenamiento en SQLite | Dejar el sistema transmitiendo durante 1 minuto. | La tabla `lecturas` registra correctamente las lecturas periódicas. | **APROBADO** |
| **P13** | Persistencia de Datos | Cerrar completamente la aplicación y volver a abrirla. | El historial mantiene intactas las lecturas previas. | **APROBADO** |
| **P14** | Verificación de Hashes DB | Inspeccionar la base de datos mediante *Database Inspector*. | La tabla `usuarios` contiene únicamente el BLOB de sal y hash PBKDF2. | **APROBADO** |

---

## 6. Integración Futura con Unidad 4 (Hardware ESP32)

El protocolo de mensajes seriales e inalámbricos cifrados en Android (`TIPO;CLAVE;VALOR`) o paquetes JSON está diseñado para comunicarse de manera directa con el **ESP32 #3 (Hub Maestro)** en la Unidad 4. 

El ESP32 #3 procesa las instrucciones recibidas desde la app Android por Bluetooth SPP / Wi-Fi TCP y las redistribuye a los nodos subordinados (**ESP32 #1 Sensores** y **ESP32 #2 Actuadores**) mediante el bus UART interno en formato JSON (`{"cmd":"DISPENSE","portion_g":60}`).

---

## 7. Guion de Demostración (5 a 7 Minutos)

1. **Autenticación (1.5 min):** Mostrar inicio de sesión como `operador`, probar intento fallido para evidenciar el hash PBKDF2 y el bloqueo de 30s.
2. **Establecimiento de Conexión (1 min):** Configurar Teléfono A como Nodo y Teléfono B como Panel con PIN de 6 dígitos. Explicar el cifrado AES-256-GCM.
3. **Monitoreo y Alerta (1.5 min):** Variar el Slider en el Nodo y observar la actualización instantánea en el Panel. Bajar del umbral crítico (20g) para disparar la notificación local.
4. **Control por Rol (1 min):** Enviar comandos de dispensado y bomba desde el operador. Cerrar e ingresar como `observador` para verificar que los controles se deshabilitan.
5. **Fail-Safe OT (1 min):** Forzar la desconexión inalámbrica y verificar cómo el Nodo entra inmediatamente en Estado Seguro.
