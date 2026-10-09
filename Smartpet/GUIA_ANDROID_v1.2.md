# SmartPet Android 1.2 — uso y prueba en dos celulares

Esta versión desarrolla el escenario Android ↔ Android de la evaluación U2: un celular simula el nodo y el otro monitorea y envía órdenes. La misma APK contiene ambos modos, escritos en Kotlin con Jetpack Compose y Material 3. Android mínimo: 8.0 (API 26).

## Instalar y conectar

1. Instala `SmartPet_App_v1.2.apk` en **los dos teléfonos**, aceptando actualizar la aplicación existente. Conserva la versión anterior como respaldo. No desinstales si quieres conservar las cuentas y el historial.
2. Conecta ambos teléfonos al mismo Wi-Fi. Mantén SmartPet en primer plano durante la demostración. Una red de invitados puede impedir la comunicación entre teléfonos.
3. Inicia sesión en ambos. La primera cuenta registrada en cada instalación es operadora; las posteriores son observadoras. Usa la cuenta operadora original para iniciar el nodo o ejecutar órdenes.
4. En el primer teléfono selecciona **Nodo IoT → Wi-Fi TCP → Iniciar Nodo Simulador**. Debe aparecer **Esperando panel**. Anota la IP y el PIN de control que muestra ese teléfono; el PIN ya no es un valor fijo de ejemplo.
5. En el otro selecciona **Panel de Control → Wi-Fi TCP**. Escribe esa IP y ese PIN, o escanea el **QR control** del nodo. Pulsa **Establecer Conexión**. Ambos deben mostrar **Conectado**; el panel debe indicar permiso de operador.
6. Si Android solicita acceso a la red local, concédelo y vuelve a pulsar el botón de conexión. Este permiso se solicita en Android 17 o superior.

Cada nodo tiene un PIN de control y otro de lectura. Una cuenta observadora o un enlace con PIN de lectura puede recibir datos, pero no ordenar dispensado, bomba ni relleno. Para probar lectura: cierra sesión en el panel, registra otra cuenta y vincúlala usando el PIN de lectura. Después vuelve a iniciar sesión con la cuenta operadora para recuperar el control.

## Qué hace cada lado

| Nodo simulador | Panel de control |
| --- | --- |
| Mueve manualmente comida entre 0 y 100 g y agua entre 0 y 100 %. | Recibe los niveles cada 2 segundos; sin enlace muestra que no hay lectura. |
| Ejecuta y confirma órdenes aceptadas. | Permite seleccionar una ración de 1 a 60 g y espera confirmación. |
| Añade la ración al terminar un dispensado simulado de 1,2 segundos; cierra el dispensador. | Muestra el estado recibido del dispensador y la bomba. |
| Impide exceder la capacidad del plato de 100 g. | Permite encender/apagar bomba, simular relleno al 95 % y detener actuadores. |
| Apaga bomba con agua menor a 20 %, desconexión o 60 segundos continuos; repetir ON mientras está encendida se rechaza. | Registra variaciones recibidas en el historial y avisa de niveles menores a 20 %. |

La bomba simula recirculación: encenderla **no aumenta** el nivel de agua. El botón de relleno es una acción separada de simulación. Los registros previos a esta actualización se identifican como anteriores sin verificación; no se generan consumos ficticios para completar el historial.

## Prueba final para grabar

| Paso | Acción | Resultado que debes mostrar |
| --- | --- | --- |
| 1 | Iniciar sesión y vincular los dos celulares. | Ambos conectados; IP real, mismo nodo y permiso correcto. |
| 2 | Bajar comida y agua desde el nodo. | El panel recibe los cambios en unos 2 segundos. |
| 3 | Dejar comida en 40 g y dispensar 30 g desde el panel. | Nodo abre/cierra dispensador; comida pasa a 70 g; panel recibe confirmación. |
| 4 | Encender y apagar bomba con agua suficiente. | Los estados coinciden en ambas pantallas; el agua no sube por encenderla. |
| 5 | Encender bomba y bajar agua a 19 %. | Nodo apaga bomba; panel muestra agua baja y bomba apagada. |
| 6 | Pulsar relleno simulado. | Ambos muestran agua al 95 % después de la confirmación del nodo. |
| 7 | Pulsar detener actuadores durante el funcionamiento. | Ambos actuadores se detienen; una ración cancelada no se añade después. |
| 8 | Abrir historial y seguir moviendo niveles en el nodo. | Aparecen los cambios recibidos; el historial se actualiza cada 2 segundos. |
| 9 | Probar PIN incorrecto y acceso de lectura. | PIN incorrecto no conecta; lectura conecta pero no habilita órdenes. |
| 10 | Cortar Wi-Fi con bomba encendida y restaurarlo. | Se detecta la pérdida de respuesta y se detienen actuadores; reconectar recupera lecturas sin activar bomba. |

Prueba adicional del límite de bomba: mantener agua suficiente, encender y esperar 60 segundos. Para estabilidad, repetir conexión/consumo/órdenes al menos tres veces y mantener una sesión varios minutos. Anota tiempos observados y errores reales; no presentes las pruebas automáticas como mediciones entre los teléfonos.

## Bluetooth

Empareja previamente los teléfonos desde los ajustes del sistema. En ambos selecciona Bluetooth y concede el permiso de dispositivos cercanos. Inicia el nodo; en el panel actualiza la lista, selecciona el teléfono nodo e introduce su PIN. Este transporte usa el mismo protocolo autenticado. Su ejecución en teléfonos físicos queda pendiente de validación; Wi-Fi es el transporte probado automáticamente para esta entrega.

## Verificación y alcance

- Se ejecutaron pruebas de simulación, cifrado/permisos, rechazo de tramas repetidas y conexiones TCP reales en la computadora. Incluyen un cliente manipulado que usa PIN de lectura e intenta enviar una orden como operador.
- Se comprobó compilación, análisis Android Lint y firma del APK. Lint conserva advertencias de estilo, recursos y versiones de dependencias; su resultado no es una certificación de seguridad.
- La validación visual, SQLite en Android y Bluetooth físico requieren repetir las pruebas en dispositivos. No hubo teléfonos conectados por ADB ni un emulador configurado durante esta preparación.
- Los usuarios deben autenticarse; cambiar credenciales exige la contraseña actual. Cerrar sesión borra el PIN remoto de memoria. El nodo diferencia autorización de control y lectura y comprueba permisos al recibir órdenes.
- Es un prototipo académico con PIN de seis dígitos. No equivale a certificación ISO ni a validación de un dispensador real. La documentación de controles y las evidencias de estabilidad de la rúbrica deben completarse con los resultados de tus pruebas.
- El protocolo de red de esta versión incluye autenticación de sesión y protección contra repetición. Actualiza ambos Android a 1.2; los scripts antiguos de PC/ESP32 y APK anteriores requieren adaptación antes de comunicarse con ella. Esta actualización no modifica el firmware de Wokwi.

Referencia de compatibilidad: [permiso de red local en Android](https://developer.android.com/privacy-and-security/local-network-permission).

## Código y respaldo

Proyecto editado: `C:\Users\Tokyotech\Desktop\Proyectos-android-studio\Smartpet`.

El archivo `SmartPet_Android_v1.2.zip` contiene el proyecto para abrir en Android Studio, sin archivos de compilación ni rutas privadas de SDK. `actualizacion_android_v1.2` conserva las fuentes anteriores, la APK 1.1 y las nuevas fuentes/pruebas preparadas. La APK está firmada para pruebas académicas; una publicación en tienda necesita preparación de lanzamiento y una clave de firma propia.
