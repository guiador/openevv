# OpenEVV para NVDA — edición local 0.4.4

Instalador: `../dist/nvda/openevv-es-0.4.4-nvda2026.3-x64.nvda-addon`.
También se entrega una copia en `Instaladores`, con sus metadatos de verificación.

Abre el `.nvda-addon`, instala y reinicia NVDA. Selecciona **OpenEVV Español —
sonido ampliado** con NVDA+Control+S. Elige la voz en la categoría Voz y abre
**Ajustes → OpenEVV** para los controles ampliados. El panel utiliza español,
inglés, alemán, francés o italiano según la voz elegida al abrirlo.

La nueva pestaña **Pausas** incluye **Pausas normales**, **Acortar al final** y
**Acortar todas**, activado inicialmente. El ajuste de pausas explícitas de NVDA
es independiente. Se añaden predicción de frases, bloqueo de idioma/velocidad/
tono/volumen, números por dígitos/pares/tríos, códigos de verificación, cinco
modos de deletreo, mayúsculas, SSML, limpieza de texto y lectura de código.

Se conservan las 64 combinaciones idioma/voz, los parámetros de textura,
perfiles de timbre, ocho frecuencias de salida, cinco modos de remuestreo,
procesamiento de nivel y ecualizador de cinco bandas. Se añaden balance
izquierda/centro/derecha, mantenimiento de audio mediante los ajustes globales
de NVDA hasta cinco minutos, prueba de texto y exportación WAV estéreo PCM16.
Aplica o guarda los cambios antes de probar o exportar.

Los diccionarios de voz, abreviaturas personales, símbolos y descripciones CLDR
de emojis se configuran en NVDA. Se prepara el código y los números antes de
los símbolos para conservar los operadores y el contexto. No se copian los
servicios, permisos, notificaciones ni opciones de batería Android.

La [comparación completa](COMPARACION-ANDROID-NVDA.md) detalla el estado de cada
función, las diferencias de procesamiento, medidas y limitaciones. Las salidas
superiores a 11.025 Hz son remuestreadas. Las reglas actuales de /s/ latinoamericana
se conservan; los perfiles de ecualizador no corrigen fonemas individualmente.

## Compatibilidad y comprobaciones

Destinado a **NVDA 2026.3 x64**; manifiesto 2026.1–2026.3. Se comprobaron los
contratos de la rama beta oficial NVDA, sintaxis Python, PE x64, exportaciones
y contenido ZIP. Las pruebas incluyen síntesis Windows real de las 64 voces,
ocho frecuencias por cinco modos, pausas, índices, exportación, cancelación y
memoria. Las secuencias y el panel se probaron con sustitutos de NVDA/wx.

El audio de una frase a 11.025 Hz coincide con el instalador de ayer en los
ocho módulos cuando se desactivan las transformaciones y el DSP nuevos. El
remuestreador C pasa pruebas con bloques de 50.000 muestras y continuidad.
Los informes están en `tests/*-results.json` y `../dist/nvda/*-info.json`.

**No se ejecutó dentro de una sesión real de NVDA 2026.3.** La instalación alpha
existente no se modificó ni reinició. La declaración del manifiesto expresa
el destino de compatibilidad, no una sesión de prueba que no se realizó.
Se requiere esa verificación final, incluido teclado, salida inalámbrica y
perfiles reales. El complemento sólo contiene la biblioteca Windows x64.

## Reproducción

`../tools/build-nvda-es.ps1` usa LLVM MinGW y Python existentes en el equipo,
aplica los parches nativos Android si hace falta, genera los controladores,
compila la DLL, ejecuta las pruebas y empaqueta el instalador. Sus parámetros
permiten elegir la carpeta de herramientas y los trabajos de compilación;
el valor inicial es un trabajo para moderar la memoria utilizada.

`prepare.py` deriva el controlador de la revisión nativa
`520585138206f1692c619ae2b45856cbbe4255b3`. `make-docs.py` genera la ayuda en
cinco idiomas. `tests/latest-features.py` ejecuta las pruebas de modos y
diccionarios e incluye las regresiones de estabilidad; `tests/panel-reset.py`
comprueba el panel y `tests/export-mode.py` comprueba los WAV con la DLL real.
El reproductor y la interfaz de NVDA se simulan; no se altera su configuración.

No hay actualizador automático. Para volver atrás, selecciona otro sintetizador
o reinstala una versión anterior que hayas conservado. Se incluyen LICENSE / NOTICE y los
créditos de OpenEVV, Stanislaw Przedzinkowski y colaboradores.

## Corrección de estabilidad 0.4.3

Los ajustes del controlador se mantienen separados del estado del hilo de
síntesis. Los cambios temporales de tono, velocidad y volumen se restauran
incluso al cancelar. La corrección se aplica mediante `stability-build.py` al
regenerar el controlador con `prepare.py`; no es necesario un `eci.ini` junto
a la DLL. NVDA conserva los ajustes en su configuración y perfiles.

Voz y Ecualizador incluyen botones de restablecimiento. Aplicar o Aceptar
guarda los cambios; Cancelar los descarta. Los créditos solicitados están en
Acerca de, en el manifiesto y en la documentación. Los resultados de las
pruebas de la DLL real y del panel simulado están en `tests/*-results.json`.
En aquella corrección la DLL no cambió respecto a la versión 0.4.2.

## Funciones recientes 0.4.4

Calidad permite elegir el modo habitual, banda ancha experimental o base nativa
de 16 kHz experimental. Esta última prepara una salida de 16 kHz. El botón de
restablecimiento recupera el modo habitual sin modificar voz o ecualizador.
Los modos se guardan por perfil y se aplican a los WAV exportados.

Diccionarios permite elegir conjuntos de Eloquence Dictionary Manager y activar
entradas personales. También lee archivos `.dic` en la carpeta `openevv` de la
configuración de NVDA; los cambios se recargan automáticamente. `latest-build.py`
incorpora las funciones del controlador upstream de manera reproducible.

La DLL incluye el tercer parche `0003-native-16khz-mode`. El parámetro 32 admite
los valores 0 (habitual), 1 (banda ancha upstream) y 2 (base nativa local de 16 kHz).
El modo es independiente por instancia. Se incluye el `eci.ini` de upstream como
metadato de compatibilidad; las preferencias siguen en la configuración de NVDA.

