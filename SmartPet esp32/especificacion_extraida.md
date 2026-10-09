SmartPet Station: Especificación Técnica, Arquitectura y Validación en Simulación v2.2

Proyecto: SmartPet Station - Dispensador e Hidratador Automatizado

Estado del Documento: Versión corregida para validación funcional en Wokwi. La entrega se considera funcional únicamente al incorporar evidencias reales de ejecución.

Autor / Departamento: Equipo de Ingeniería de Hardware y Sistemas Embebidos

Fecha de Emisión: 05 de Septiembre de 2026 (Actualizado v2.2)

Grupo: Jerson Rivas – Cristian León – Sebastián Ibáñez

Corrección aplicada a la retroalimentación del Paso 7/8: se elimina la presentación del proyecto como exclusivamente conceptual y se incorpora una sección específica de implementación simulada, evidencias verificables y matriz de pruebas. No se declara construcción física: el alcance de esta entrega es una simulación funcional en Wokwi.

1. Resumen de la Arquitectura Multi-Nodo

El sistema "SmartPet Station" adopta una arquitectura de hardware distribuida compuesta por tres microcontroladores ESP32 operando en un esquema modular jerárquico. Esta separación funcional segmentada evita el bloqueo de hilos de ejecución (thread-blocking) causado por el monitoreo continuo de sensores y la generación de señales PWM para actuadores, garantizando un rendimiento en tiempo real altamente confiable.

Los tres nodos ESP32 se interconectan físicamente mediante un bus de comunicación Serial UART (Universal Asynchronous Receiver-Transmitter) asíncrono, operando a una velocidad de transmisión de 115,200 baudios. Los datos e instrucciones son empaquetados en tramas con formato estructurado JSON (JavaScript Object Notation), lo que simplifica el parseo, la validación de integridad y la extensibilidad del protocolo.

Justificación de Redundancia Local (RTC DS3231)

Para garantizar la continuidad operativa crítica (alimentación e hidratación de la mascota) ante eventuales caídas de la red Wi-Fi o interrupciones en el servicio de Internet/Cloud, el sistema incorpora un módulo de reloj en tiempo real de alta precisión RTC DS3231 respaldado por una batería de litio CR2032. Esta redundancia asegura que el esquema de alimentación programado se mantenga totalmente autónomo e ininterrumpido sin importar el estado de la conectividad de red.

2. Distribución y Roles de los Módulos ESP32

2.1 ESP32 #3 - Hub Líder del Sistema (Master / Connectivity Bridge)

El ESP32 #3 actúa como la unidad central de orquestación y enlace con el exterior. Sus responsabilidades exclusivas incluyen:

Gestión de Comunicaciones Inalámbricas: Uso de los stacks integrados de Wi-Fi 802.11 b/g/n y Bluetooth v4.2 BLE para la sincronización con el servidor Cloud/MQTT y la aplicación móvil.

Sincronización Horaria RTC: Lectura y actualización constante del módulo RTC DS3231 mediante la interfaz de bus I2C.

Orquestación del Bus UART: Envío de comandos de dispensado hacia el ESP32 #2 y consolidación de las métricas recibidas desde el ESP32 #1.

Aclaración de componentes excluidos: El ESP32 #3 no gestiona etapas de potencia ni conmutación de cargas eléctricas. Tampoco incluye módulo de cámara (ej. OV2640) ni requiere antenas o tarjetas de red externas, utilizando exclusivamente el Front-End de RF integrado en el SoC.

2.2 ESP32 #1 - Captura de Sensores (Sensor Node)

El ESP32 #1 está dedicado exclusivamente a la adquisición, filtrado y digitalización de las variables métricas del depósito y plato:

Nivel de Comida: Sensor de proximidad infrarrojo industrial E18-D80NK protegido por acrílico transparente para calcular la presencia de croquetas en la tolva.

Nivel de Agua: Sensor magnético de flotador de estado binario (Lleno/Vacío) para prevenir el funcionamiento en seco de la bomba.

Flujo de Agua: Sensor de efecto Hall YF-S201 montado en la línea de recirculación para verificar la circulación efectiva del agua.

Masa Consumida: Celda de carga con capacidad de 5 kg conectada al conversor A/D de 24 bits HX711 para medir el peso de alimento consumido.

2.3 ESP32 #2 - Control de Actuadores (Actuator Node)

El ESP32 #2 asume la ejecución de los comandos físicos requeridos por el sistema, garantizando un control preciso:

Mecanismo Dispensador de Alimento: Control por modulación por ancho de pulsos (PWM) sobre un servomotor de alto torque MG996R para la apertura programada de la tolva.

Sistema de Filtrado y Recirculación de Agua: Activación de una bomba sumergible de 12V DC gobernada mediante un driver MOSFET de potencia Canal N (IRF520) provisto de un Diodo Flyback (1N4007) antiparalelo.

3. Bloque Independiente de Gestión de Energía

El sistema de energía se ha diseñado de forma centralizada pero dividida en rieles de voltaje independientes para evitar que el ruido de conmutación de los motores o acoples inductivos afecte la estabilidad digital de los microcontroladores.

Fuente Principal de Entrada: Adaptador AC/DC regulado a +12V DC con capacidad de corriente de 5A nominales, con fusible de acción rápida de 3.15A.

Riel +12V DC Directo: Suministro dedicado exclusivamente a los actuadores de potencia (bomba sumergible de agua).

Riel +5.0V DC Regulación Buck: Convertidor DC-DC Step-Down tipo Buck LM2596 reduce la línea de 12V a +5.0V DC estables. Este riel alimenta directamente los pines VIN/5V de las tres placas ESP32, el servomotor MG996R, el módulo RTC DS3231 y el amplificador HX711.

Medidas de Protección: Diodo Flyback (1N4007) antiparalelo en la bomba, capacitor electrolítico de 470µF en la salida del módulo Buck LM2596 y masa común (GND unificado) entre todos los módulos.

4. Lógica de Verificación Temporal y Protocolo de Comunicación

4.1 Lógica del Firmware y Verificación Temporal

Para evitar el colapso del procesador y asegurar una temporización precisa sin utilizar bucles bloqueantes (delay), el firmware desarrollado en C++/Arduino utiliza millis() combinado con el módulo RTC DS3231 cada 1000 ms. Su comportamiento deberá verificarse mediante la evidencia de simulación incluida en la Sección 7.

4.2 Formato de Tramas JSON (Bus UART)

Ejemplo 1: Comando de Dispensado (ESP32 #3 → ESP32 #2)

{  "cmd": "DISPENSE",  "portion_g": 60,  "timestamp": 1724250000,  "msg_id": 4021}

Ejemplo 2: Reporte de Sensores (ESP32 #1 → ESP32 #3)

{  "node": "ESP32_1",  "food_status": "FULL",  "water_status": "FULL",  "flow_rate_lmin": 1.45,  "dish_weight_g": 52.3,  "status": "OK"}

4.3 Firmware ESP32 #1: Nodo de Sensores

/* * SmartPet Station - ESP32 #1 (Sensor Node) * Responsabilidad: Adquisición de métricas de tolva (Infrarrojo E18-D80NK), nivel, flujo y masa consumida. */#include <Arduino.h>#include <ArduinoJson.h>#include "HX711.h"// Definición de Pines según Matriz Técnicaconst int PIN_IR_FOOD = 5;    // Sensor Infrarrojo E18-D80NK (Tolva)const int PIN_FLOAT = 19;     // Sensor flotador de agua (Pull-Up)const int PIN_FLOW = 23;      // Sensor de flujo YF-S201 (Interrupción)const int PIN_HX711_DT = 4;   // HX711 Dataconst int PIN_HX711_SCK = 2;  // HX711 Relojconst int UART_RX = 16;const int UART_TX = 17;const float FLOW_CALIBRATION = 7.5;const float SCALE_CALIBRATION = 420.09;HX711 scale;volatile unsigned int flowPulseCount = 0;unsigned long lastSendTime = 0;const unsigned long SEND_INTERVAL = 3000;void IRAM_ATTR pulseCounterISR() {  flowPulseCount++;}void setup() {  Serial.begin(115200);  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);  pinMode(PIN_IR_FOOD, INPUT_PULLUP);  pinMode(PIN_FLOAT, INPUT_PULLUP);  pinMode(PIN_FLOW, INPUT_PULLUP);  attachInterrupt(digitalPinToInterrupt(PIN_FLOW), pulseCounterISR, RISING);  scale.begin(PIN_HX711_DT, PIN_HX711_SCK);  scale.set_scale(SCALE_CALIBRATION);  scale.tare();  Serial.println("[ESP32 #1] Sensor Node Inicializado correctamente.");}void loop() {  unsigned long currentMillis = millis();  if (currentMillis - lastSendTime >= SEND_INTERVAL) {    unsigned long elapsedTime = currentMillis - lastSendTime;    lastSendTime = currentMillis;    // LOW = Obstruido/Comida detectada, HIGH = Vacío    String foodStatus = (digitalRead(PIN_IR_FOOD) == LOW) ? "FULL" : "EMPTY";    String waterStatus = (digitalRead(PIN_FLOAT) == LOW) ? "FULL" : "EMPTY";    noInterrupts();    unsigned int pulses = flowPulseCount;    flowPulseCount = 0;    interrupts();    float flowRate = ((pulses / (elapsedTime / 1000.0)) / FLOW_CALIBRATION);    float dishWeight = scale.is_ready() ? scale.get_units(3) : 0.0;    if (dishWeight < 0) dishWeight = 0.0;    StaticJsonDocument<256> doc;    doc["node"] = "ESP32_1";    doc["food_status"] = foodStatus;    doc["water_status"] = waterStatus;    doc["flow_rate_lmin"] = serialized(String(flowRate, 2));    doc["dish_weight_g"] = serialized(String(dishWeight, 1));    doc["status"] = "OK";    serializeJson(doc, Serial2);    Serial2.println();    serializeJson(doc, Serial);    Serial.println();  }}

4.4 Firmware ESP32 #2: Nodo de Actuadores

/* * SmartPet Station - ESP32 #2 (Actuator Node) * Responsabilidad: Control de servomotor dosificador (MG996R) y bomba 12V (MOSFET IRF520). */#include <Arduino.h>#include <ArduinoJson.h>#include <ESP32Servo.h>const int PIN_SERVO_PWM = 13;const int PIN_MOSFET_PUMP = 12;const int UART_RX = 16;const int UART_TX = 17;Servo dispenserServo;const int SERVO_CLOSED_ANGLE = 0;const int SERVO_OPEN_ANGLE = 85;bool isDispensing = false;unsigned long dispenseStartTime = 0;unsigned long dispenseDuration = 0;void executeDispense(int grams) {  Serial.printf("[ESP32 #2] Ejecutando dispensado de %d gramos...\n", grams);  dispenseDuration = constrain(grams * 40, 800, 5000);  dispenserServo.write(SERVO_OPEN_ANGLE);  dispenseStartTime = millis();  isDispensing = true;}void setPumpState(bool state) {  digitalWrite(PIN_MOSFET_PUMP, state ? HIGH : LOW);  Serial.printf("[ESP32 #2] Bomba de agua: %s\n", state ? "ENCENDIDA" : "APAGADA");}void setup() {  Serial.begin(115200);  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);  pinMode(PIN_MOSFET_PUMP, OUTPUT);  digitalWrite(PIN_MOSFET_PUMP, LOW);  ESP32PWM::allocateTimer(0);  dispenserServo.setPeriodHertz(50);  dispenserServo.attach(PIN_SERVO_PWM, 500, 2400);  dispenserServo.write(SERVO_CLOSED_ANGLE);  Serial.println("[ESP32 #2] Actuator Node Inicializado.");}void loop() {  if (isDispensing && (millis() - dispenseStartTime >= dispenseDuration)) {    dispenserServo.write(SERVO_CLOSED_ANGLE);    isDispensing = false;    Serial.println("[ESP32 #2] Dispensado completado. Tolva cerrada.");  }  if (Serial2.available() > 0) {    String jsonString = Serial2.readStringUntil('\n');    jsonString.trim();    if (jsonString.length() > 0) {      StaticJsonDocument<256> doc;      DeserializationError error = deserializeJson(doc, jsonString);      if (!error) {        const char* cmd = doc["cmd"];        if (cmd && strcmp(cmd, "DISPENSE") == 0) {          int grams = doc["portion_g"] | 50;          executeDispense(grams);        } else if (cmd && strcmp(cmd, "PUMP") == 0) {          bool state = doc["state"] | false;          setPumpState(state);        }      }    }  }}

4.5 Firmware ESP32 #3: Hub Maestro y Enlace

/* * SmartPet Station - ESP32 #3 (Hub / Master / Bridge) * Responsabilidad: Orquestación, sincronización RTC DS3231, conectividad y bus UART. */#include <Arduino.h>#include <Wire.h>#include <RTClib.h>#include <ArduinoJson.h>#include <WiFi.h>const int PIN_I2C_SDA = 21;const int PIN_I2C_SCL = 22;const int UART_RX = 16;const int UART_TX = 17;const int TARGET_HOUR = 8;const int TARGET_MIN = 30;const int TARGET_GRAMS = 60;RTC_DS3231 rtc;unsigned long lastRTCCheck = 0;const unsigned long RTC_INTERVAL = 1000;bool dispenseExecutedToday = false;unsigned long messageCounter = 1000;void sendUARTDispenseCommand(int grams, uint32_t epochTime) {  StaticJsonDocument<256> doc;  doc["cmd"] = "DISPENSE";  doc["portion_g"] = grams;  doc["timestamp"] = epochTime;  doc["msg_id"] = ++messageCounter;  serializeJson(doc, Serial2);  Serial2.println();}void setup() {  Serial.begin(115200);  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);  if (!rtc.begin()) {    Serial.println("[ESP32 #3] ERROR: No se detecta el módulo RTC DS3231.");  } else if (rtc.lostPower()) {    rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));  }  WiFi.mode(WIFI_STA);  Serial.println("[ESP32 #3] Hub Líder inicializado con éxito.");}void loop() {  unsigned long currentMillis = millis();  if (currentMillis - lastRTCCheck >= RTC_INTERVAL) {    lastRTCCheck = currentMillis;    DateTime now = rtc.now();    if (now.hour() == TARGET_HOUR && now.minute() == TARGET_MIN) {      if (!dispenseExecutedToday) {        sendUARTDispenseCommand(TARGET_GRAMS, now.unixtime());        dispenseExecutedToday = true;      }    } else {      dispenseExecutedToday = false;    }  }  if (Serial2.available() > 0) {    String telemetryStr = Serial2.readStringUntil('\n');    telemetryStr.trim();    if (telemetryStr.length() > 0) {      StaticJsonDocument<300> doc;      DeserializationError err = deserializeJson(doc, telemetryStr);      if (!err && doc.containsKey("node")) {        Serial.println("[ESP32 #3 <- Telemetría Recibida]");        serializeJsonPretty(doc, Serial);        Serial.println();      }    }  }}

5. Análisis de Brechas de Seguridad IoT (CE 1.1.2)

Dado que la arquitectura integra conectividad inalámbrica (Wi-Fi/BLE) y comunicación serial interna (UART), se identifican las siguientes brechas y mitigaciones técnicas:

Intercepción de Tráfico Inalámbrico (Man-in-the-Middle / Sniffing):La Brecha: El ESP32 #3 utiliza Wi-Fi para conectarse a la nube. Si los datos se envían en texto claro, un atacante en la red puede capturar telemetría o credenciales.Mitigación: Implementación obligatoria de TLS 1.2 sobre MQTTS o HTTPS cifrando el canal extremo a extremo.

Ataques de Inyección y Spoofing en el Bus UART:La Brecha: La comunicación inter-nodo en jumpers expuestos permite que un intruso conecte un microcontrolador externo a los pines GPIO 16/17 e inyecte comandos maliciosos de dispensado continuo.Mitigación: Incorporación de firma HMAC/Hash temporal o un token de autenticación en cada payload JSON antes de la ejecución de actuadores.

Vulnerabilidad en el Emparejamiento Bluetooth (BLE Hijacking):La Brecha: Si el BLE opera en modo "Just Works" permanente, terceros en un rango de 10 metros pueden sobreescribir la configuración horaria o de red.Mitigación: Requerir Passkey Entry (código PIN numérico) y activar un timeout en firmware que desconecte y apague la radio BLE tras 5 minutos de inactividad.

6. Matriz de Asignación de Pines (Pinout Table)

Módulo ESP32

Componente

Pin GPIO / Bus

Función / Protocolo

ESP32 #3 (Hub)

RTC DS3231

GPIO 21

I2C (SDA) - Datos de Tiempo Real

ESP32 #3 (Hub)

RTC DS3231

GPIO 22

I2C (SCL) - Reloj I2C

ESP32 #3 (Hub)

Bus UART Inter-Nodo

GPIO 17 (TX2)

UART Inter-Nodo (Línea Transmisión)

ESP32 #3 (Hub)

Bus UART Inter-Nodo

GPIO 16 (RX2)

UART Inter-Nodo (Línea Recepción)

ESP32 #1 (Sensores)

Sensor Infrarrojo E18-D80NK

GPIO 5

Digital IN (Pull-Up, Estado Nivel Comida)

ESP32 #1 (Sensores)

Flotador Agua

GPIO 19

Digital IN (Pull-Up, Estado Nivel Agua)

ESP32 #1 (Sensores)

YF-S201 (Flujo)

GPIO 23

Digital IN (Interrupt, Pulsos Hall)

ESP32 #1 (Sensores)

HX711 (Peso)

GPIO 4

Digital IN (HX711 DT - Datos)

ESP32 #1 (Sensores)

HX711 (Peso)

GPIO 2

Digital OUT (HX711 SCK - Reloj)

ESP32 #1 (Sensores)

Bus UART Inter-Nodo

GPIO 17 / 16

UART (TX2/RX2 Comunicación)

ESP32 #2 (Actuadores)

Servomotor MG996R

GPIO 13

PWM Channel 0 (Señal de Control)

ESP32 #2 (Actuadores)

MOSFET Bomba Agua

GPIO 12

Digital OUT (Gating MOSFET IRF520)

ESP32 #2 (Actuadores)

Bus UART Inter-Nodo

GPIO 17 / 16

UART (TX2/RX2 Comunicación)

7. Implementación y Evidencia de Simulación en Wokwi (Paso 7)

Para responder a la observación de falta de evidencia de implementación, esta sección debe documentar una ejecución real del sistema en Wokwi. Las capturas incorporadas deben provenir del proyecto efectivamente ejecutado y corresponder al firmware presentado en las Secciones 4.3, 4.4 y 4.5. Una imagen ilustrativa o un diagrama creado fuera del simulador no reemplaza la evidencia. Si algún sensor exacto no está disponible en Wokwi, puede utilizarse un componente virtual equivalente para estimular la misma entrada lógica, indicando expresamente esa sustitución.

7.1 Montaje de Conexiones Utilizado como Referencia de Simulación

La siguiente tabla funciona como lista de verificación del cableado. La captura general de Wokwi debe mostrar las conexiones lógicas principales entre los tres ESP32, las entradas de sensores simuladas y las salidas hacia actuadores o indicadores equivalentes.

Bloque de Conexión

Línea Origen / Señal

Línea Destino / Componente

Propósito Técnico

Alimentación Primaria

Jack DC (+12V DC)

Fusible 3.15A → LM2596 (IN+) y VCC Bomba

Línea de potencia protegida

Regulación Lógica

Salida LM2596 (+5V DC)

VIN ESP32 #1, #2, #3, VCC MG996R, VCC HX711

Riel estabilizado de control (con 470µF desacoplo)

Referencia Unificada

GND Central (Bornera)

GND de Fuente 12V, LM2596, ESP32s, MOSFET y Sensores

Eliminación de bucles de masa y ruido flotante

Bus UART Inter-Nodo

ESP32 #3 (TX2 - GPIO 17)

ESP32 #1 (RX2) y ESP32 #2 (RX2)

Canal compartido de transmisión serial JSON

Bus UART Retorno

ESP32 #1 (TX2 - GPIO 17)

ESP32 #3 (RX2 - GPIO 16)

Reporte de telemetría hacia el nodo Hub

Etapa de Potencia Hidráulica

ESP32 #2 (GPIO 12) → Gate IRF520

Drain IRF520 → Bomba (-) | Diodo Flyback 1N4007 en bornes

Conmutación segura contra picos inductivos

Sensado Infrarrojo

E18-D80NK (Señal Digital)

ESP32 #1 (GPIO 5 con Pull-Up interno)

Detección de presencia física de croquetas

7.2 Evidencia Visual de Ejecución

Enlace público o compartido del proyecto Wokwi: ________________________________________________

IMPORTANTE: antes de entregar, sustituir cada recuadro de esta subsección por una captura REAL. No dejar recuadros pendientes en la versión final.

Figura 1. Vista general del montaje en Wokwi

Figura 2. Inicialización de los nodos

Figura 3. Telemetría UART-JSON ESP32 #1 → ESP32 #3

Figura 4. Comando DISPENSE y respuesta del actuador

Figura 5. Prueba de la salida de bomba

7.3 Matriz de Pruebas Funcionales y Evidencia

Completar la columna “Resultado real” después de ejecutar cada prueba. El estado “APROBADO” solo debe usarse si existe evidencia observable en Wokwi o en el Monitor Serial.

ID

Prueba

Acción / Entrada

Resultado esperado

Evidencia

Resultado real

TC-01

Inicialización de nodos

Iniciar la simulación.

Los tres nodos reportan su inicialización sin errores críticos.

Figura 2

PENDIENTE

TC-02

Lectura de sensores

Modificar las entradas virtuales de comida/agua y, cuando corresponda, flujo/peso.

ESP32 #1 genera una trama JSON coherente con el cambio realizado.

Figura 3

PENDIENTE

TC-03

Recepción de telemetría

Permitir que ESP32 #1 transmita por UART.

ESP32 #3 recibe y muestra la trama del nodo ESP32_1.

Figura 3

PENDIENTE

TC-04

Dispensado programado

Ajustar temporalmente la hora objetivo para coincidir con la simulación o activar el caso de prueba.

ESP32 #3 envía DISPENSE y ESP32 #2 abre/cierra el servo una sola vez en el intervalo.

Figura 4

PENDIENTE

TC-05

Control de bomba

Enviar comando PUMP con state=true y luego state=false.

GPIO 12 cambia de estado y el indicador/actuador simulado responde.

Figura 5

PENDIENTE

TC-06

No bloqueo del ciclo

Mantener la simulación durante varias transmisiones y una actuación.

La telemetría continúa periódicamente y el servo retorna al estado cerrado sin detener el programa.

Figuras 3-4

PENDIENTE

8. Validación Funcional, Conclusiones y Próximos Pasos (Paso 8)

La arquitectura multi-nodo distribuida de SmartPet Station queda definida a nivel de hardware, comunicación y firmware. Sin embargo, la conclusión funcional no debe basarse únicamente en la especificación: debe estar respaldada por las evidencias de la Sección 7 y por los resultados reales de la matriz de pruebas.

Resultado de la validación:

Estado final de la simulación: [APROBADO / APROBADO CON OBSERVACIONES / PENDIENTE]

Resumen de resultados observados:

Limitaciones o sustituciones utilizadas en Wokwi: 

Criterio de cierre de la entrega: adjuntar capturas reales de Wokwi, registrar el resultado de cada caso de prueba y mantener el enlace al proyecto simulado disponible para revisión.

Próximo paso posterior a la validación: ruteo Gerber y diseño de PCB unificada en reemplazo de protoboards.

Próximo paso posterior a la validación: implementar TLS/MQTTS y el mecanismo de autenticación de mensajes definido en el análisis de seguridad.