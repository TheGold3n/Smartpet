from pathlib import Path
import re

base = Path(__file__).parent
src = base / 'respaldo_v1.2' / 'main' / 'java' / 'cl' / 'inacap' / 'smartpet'
dest = base / 'nuevo'
(dest / 'ui').mkdir(parents=True, exist_ok=True)
extra = '''import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
'''
for name in ['PantallaLogin', 'PantallaConexion', 'PantallaPanel', 'PantallaNodo']:
    s = (src / 'ui' / (name + '.kt')).read_text(encoding='utf-8')
    s = s.replace('package cl.inacap.smartpet.ui\n', 'package cl.inacap.smartpet.ui\n\n' + extra, 1)
    s, count = re.subn(r'    Column\(Modifier.fillMaxSize\(\).safeDrawingPadding\(\).verticalScroll\(rememberScrollState\(\)\).padding\(\d+\.dp\),\n        verticalArrangement = Arrangement.spacedBy\(\d+\.dp\)\) \{', '    PetScreen {', s, count=1)
    assert count == 1, name
    s = s.replace('Card(Modifier.fillMaxWidth()) {', '''Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {''')
    if name == 'PantallaLogin':
        s = s.replace('''        Text("SmartPet Station", style = MaterialTheme.typography.headlineLarge)
        Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge)
''', '''        PetBrand("SmartPet Station", "Acceso Seguro al Sistema", centered = true)
        PetCard {
        Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
''')
        s = s.replace('label = { Text("Usuario") }, singleLine', 'label = { Text("Usuario") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PetOrange) }, singleLine')
        s = s.replace('visualTransformation = PasswordVisualTransformation(), singleLine', 'leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PetOrange) }, visualTransformation = PasswordVisualTransformation(), singleLine')
        s = s.replace('modifier = Modifier.fillMaxWidth()) { Text(if (ocupado)', 'modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (ocupado)')
        s = s.rstrip()[:-1].rstrip() + '\n    }\n}\n'
    elif name == 'PantallaConexion':
        s = s.replace('Text("Vincular SmartPet", style = MaterialTheme.typography.headlineMedium)', 'PetBrand("Vincular Estación", "Configuración del Enlace Seguro", centered = true)')
        s = s.replace('        Text("Modo de operación",', '        PetCard {\n        Text("Modo de operación",')
        s = s.replace('        Text("Medio de comunicación",', '        HorizontalDivider(color = Color(0xFFEEEEEE))\n        Text("Medio de comunicación",')
        s = s.replace('label = { Text("Panel de Control") }', 'leadingIcon = { Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFE0B2)), label = { Text("Panel de Control", fontSize = 12.sp) }')
        s = s.replace('label = { Text("Nodo IoT") }', 'leadingIcon = { Icon(Icons.Default.Sensors, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFE0B2)), label = { Text("Nodo IoT", fontSize = 12.sp) }')
        s = s.replace('label = { Text("Wi-Fi TCP") }', 'leadingIcon = { Icon(Icons.Default.Wifi, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE1F5FE), selectedLabelColor = Color(0xFF0277BD)), label = { Text("Wi-Fi TCP") }')
        s = s.replace('label = { Text("Bluetooth") }', 'leadingIcon = { Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE8EAF6)), label = { Text("Bluetooth") }')
        s = s.replace('singleLine = true, keyboardOptions = KeyboardOptions', 'leadingIcon = { Icon(Icons.Default.Lock, null, tint = PetOrange) }, singleLine = true, keyboardOptions = KeyboardOptions')
        s = s.replace('label = { Text("IP del celular nodo") }, singleLine', 'label = { Text("IP del celular nodo") }, leadingIcon = { Icon(Icons.Default.Router, null, tint = PetBlue) }, singleLine')
        s = s.replace('}) { Text("Escanear QR del nodo") }', '}, colors = ButtonDefaults.outlinedButtonColors(containerColor = PetBrown, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Escanear QR del nodo") }')
        s = s.replace('}, modifier = Modifier.fillMaxWidth()) { Text(if (nodo)', '}, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (nodo)')
        s = s.replace('        OutlinedButton(onClick = { credenciales = true }', '        }\n        OutlinedButton(onClick = { credenciales = true }')
    elif name == 'PantallaPanel':
        s = s.replace('''        Text("SmartPet Station", style = MaterialTheme.typography.headlineMedium)
        Text("Panel de Control · $estado")''', '''        PetBrand("SmartPet Station", "Dispensador Inteligente")
        PetConnectionState(estado, detalle)''')
        s = s.replace('        if (detalle.isNotEmpty()) Text(detalle)\n', '')
        s = s.replace('        Text("Usuario: ${Sesion.usuarioActual} · Rol: $rol")', '''        PetCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AccountCircle, null, tint = PetBrown)
                Text("Usuario: ${Sesion.usuarioActual} · Rol: $rol", fontWeight = FontWeight.SemiBold)
            }
        }''')
        s = s.replace('        TarjetaLectura("Comida en el plato", comida, "g")\n        TarjetaLectura("Agua en el estanque", agua, "%")\n', '')
        s = s.replace('                Text("Dispensador:', '                PetReading("Comida en el plato", comida, false)\n                Spacer(Modifier.height(8.dp))\n                Text("Dispensador:', 1)
        s = s.replace('                Text("Bomba:', '                PetReading("Estanque de Agua", agua, true)\n                Spacer(Modifier.height(8.dp))\n                Text("Bomba:', 1)
        s = s.replace('enabled = habilitado && !bomba', 'colors = ButtonDefaults.buttonColors(containerColor = PetBlue, contentColor = Color.White), enabled = habilitado && !bomba')
        s = s.replace('}, enabled = habilitado, modifier = Modifier.fillMaxWidth()) { Text("Simular relleno', '}, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0277BD), containerColor = Color(0xFFE1F5FE)), enabled = habilitado, modifier = Modifier.fillMaxWidth()) { Text("Simular relleno')
        s = s.replace('{ Text("Ver historial de la simulación") }', '{ Icon(Icons.Default.History, null); Spacer(Modifier.width(8.dp)); Text("Ver historial de la simulación") }')
        s = s[:s.index('\n@Composable\nprivate fun TarjetaLectura')] + '\n'
    elif name == 'PantallaNodo':
        s = s.replace('''        Text("Nodo Simulador", style = MaterialTheme.typography.headlineMedium)
        Text("Estado: $estado")
        if (detalle.isNotEmpty()) Text(detalle)''', '''        PetBrand("Nodo Simulador", "SmartPet Station · Estación IoT")
        PetConnectionState(estado, detalle)''')
        s = s.replace('                Text("Comida en el plato: ${"%.1f".format(peso ?: 0.0)} g", style = MaterialTheme.typography.titleMedium)', '                PetReading("Comida en el plato", peso, false)')
        s = s.replace('                Text("Agua: ${"%.1f".format(agua ?: 0.0)} %", style = MaterialTheme.typography.titleMedium)', '                PetReading("Estanque de Agua", agua, true)')
        s = s.replace('onValueChange = { Sesion.ajustarAgua(it) }, valueRange', 'onValueChange = { Sesion.ajustarAgua(it) }, colors = SliderDefaults.colors(thumbColor = PetBlue, activeTrackColor = PetBlue), valueRange')
        s = s.replace('''        Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}")
        Text("Bomba: ${if (bomba) "ENCENDIDA" else "APAGADA"}")''', '''        PetCard {
            Text("Actuadores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Dispensador: ${if (dispensador) "ABIERTO" else "CERRADO"}")
            Text("Bomba: ${if (bomba) "ENCENDIDA" else "APAGADA"}")
        }''')
    (dest / 'ui' / (name + '.kt')).write_text(s, encoding='utf-8')

main = (src / 'MainActivity.kt').read_text(encoding='utf-8')
main = main.replace('import androidx.compose.ui.graphics.Color', 'import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.graphics.Color')
main = main.replace('background = Color(0xFFFFF9F2), surface = Color(0xFFFFF9F2), surfaceVariant = Color(0xFFFFEAD8)))', '''background = PetCream, surface = PetCream, surfaceVariant = Color(0xFFFFE0B2),
                onPrimary = Color.White, onBackground = PetBrown, onSurface = PetBrown, onSurfaceVariant = PetBrown),
                shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)))''')
(dest / 'MainActivity.kt').write_text(main, encoding='utf-8')
(dest / 'ui' / 'EstiloSmartPet.kt').write_text((base / 'EstiloSmartPet.kt').read_text(encoding='utf-8'), encoding='utf-8')
print('Diseño preparado: login, vinculación, panel y nodo. Lógica de sesión y transportes conservada.')
