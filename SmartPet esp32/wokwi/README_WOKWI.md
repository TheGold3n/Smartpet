# Guía Rápida para Simular los 3 Nodos en Wokwi (Entrega v2.2)

Esta carpeta contiene los **3 proyectos independientes** correspondientes a los 3 microcontroladores ESP32 de la arquitectura multi-nodo de **SmartPet Station**.

---

## 📁 Carpetas de los Nodos para Wokwi

1. [`wokwi/esp32_1_sensors/`](esp32_1_sensors/)
   * **Componentes:** ESP32, Switch IR Tolva (GPIO 5), Switch Flotador Agua (GPIO 19), Pulsador Caudalímetro (GPIO 23).
   * **Objetivo:** Adquirir variables y emitir telemetría JSON por UART cada 2 segundos.
   * **Evidencias:** Genera la captura de la **Figura 2** (inicialización) y **Figura 3** (telemetría JSON).

2. [`wokwi/esp32_2_actuators/`](esp32_2_actuators/)
   * **Componentes:** ESP32, Servomotor MG996R (GPIO 13), LED indicador Bomba 12V (GPIO 12).
   * **Objetivo:** Recibir comandos JSON (`DISPENSE`, `PUMP`), operar el servo/bomba y emitir ACK.
   * **Evidencias:** Genera la captura de la **Figura 4** (tolva abriendo y cerrando) y **Figura 5** (LED bomba encendido/apagado).

3. [`wokwi/esp32_3_hub/`](esp32_3_hub/)
   * **Componentes:** ESP32, Reloj RTC DS1307/DS3231 I2C (GPIO 21 SDA, GPIO 22 SCL).
   * **Objetivo:** Orquestar el sistema, derivar clave criptográfica AES-256 con PBKDF2 y coordinar el bus UART.
   * **Evidencias:** Valida la inicialización del Hub y la sincronización horaria autónoma.

---

## ⚡ Paso a Paso para Ejecutar en Wokwi (Gratis y en 2 minutos)

1. Abre tu navegador e ingresa a: **[https://wokwi.com/projects/new/esp32](https://wokwi.com/projects/new/esp32)**
2. Para cada nodo:
   * **Pestaña `sketch.ino`:** Copia y pega el contenido del archivo `sketch.ino` de la carpeta del nodo.
   * **Pestaña `diagram.json`:** Copia y pega el contenido de `diagram.json` (verás aparecer los componentes en la pantalla).
   * **Pestaña `libraries.txt`:** Copia y pega el contenido de `libraries.txt`.
3. Haz clic en el botón verde **"Play" (Iniciar simulación)**.
4. Toma las capturas de pantalla para tu informe Word y copia el enlace de Wokwi usando el botón **"Share"**.

