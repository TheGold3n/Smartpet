# SmartPet TI3042 U2

Repositorio combinado para reconstruir el proyecto SmartPet en Android Studio y revisar sus componentes IoT.

## Contenido

- `Smartpet/`: proyecto Android Studio versión 1.3, Kotlin, Jetpack Compose y pruebas automatizadas. Ábrelo directamente en Android Studio para sincronizar Gradle y volver a generar la APK.
- `SmartPet esp32/`: firmware ESP32, proyectos Wokwi, documentación técnica, guía Android y materiales de evaluación de la Unidad 2. También conserva fuentes de actualizaciones anteriores para referencia.

Las dos carpetas se copiaron desde sus ubicaciones originales; esta estructura combinada es una copia para GitHub. No se incluyen APKs, ZIPs de respaldo, salidas de compilación, cachés Gradle ni configuración local como `local.properties`.

## Reconstruir Android

1. Descarga o clona este repositorio.
2. Abre la carpeta `Smartpet/` en Android Studio.
3. Deja que Android Studio sincronice Gradle y configura un dispositivo o emulador.
4. Compila la configuración `app` para generar una APK nueva.

## Alcance

La app Android 1.3 incluye un nodo simulador y un panel para demostraciones entre dos celulares por Wi-Fi; el código también incluye transporte Bluetooth, cuya prueba física debe validarse por separado. Los componentes ESP32/Wokwi y el antiguo servidor Python son proyectos independientes y pueden usar protocolos diferentes al de Android 1.3. Es un prototipo académico, no una certificación ISO ni una validación de actuadores físicos.
