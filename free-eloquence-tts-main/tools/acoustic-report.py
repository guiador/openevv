"""Construye el informe en español y una galería accesible de los resultados."""
import html, json, math, wave
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'investigacion/klatt-2026-10-06'
data=json.loads((OUT/'resultados.json').read_text(encoding='utf8'))
records={r['file']:r for r in data['measurements']}
normalizations=[]
for lang in ['eses','esus']:
    for kind,rate in [('directa',11025),('sinc',32000),('directa',32000)]:
        name=f'{lang}_frase_{kind}_{rate}.wav'
        with wave.open(str(OUT/name),'rb') as w:
            fs=w.getframerate();x=np.frombuffer(w.readframes(w.getnframes()),dtype='<i2').astype(float)
        gain=2000/np.sqrt(np.mean(x*x))
        target=name.replace('.wav','_escucha.wav')
        y=np.rint(x*gain)
        assert abs(y).max()<32767
        with wave.open(str(OUT/target),'wb') as w:
            w.setparams((1,2,fs,len(y),'NONE','not compressed'));w.writeframes(y.astype('<i2').tobytes())
        normalizations.append(dict(source=name,file=target,rms_target_pcm=2000,gain_db=20*math.log10(gain)))
(OUT/'normalizacion-escucha.json').write_text(json.dumps(normalizations,indent=2),encoding='utf8')

vowel_rows=[]
for v in 'aeiou':
    r=records[f'eses_vocal_{v}_11025.wav'];t=r['vowel_targets'];m=r['vowel_lpc_estimates_hz']
    vowel_rows.append(f'| /{v}/ | {t["f1"]:.0f}, {t["f2"]:.0f}, {t["f3"]:.0f} | {m["f1"]:.0f}, {m["f2"]:.0f}, {m["f3"]:.0f} |')
rate_rows=[]
for kind,rate in [('directa',11025),('sinc',32000),('directa',22050),('directa',24000),('directa',32000),('directa',44100)]:
    r=records[f'eses_frase_{kind}_{rate}.wav'];base=records['eses_frase_directa_11025.wav']['rms']
    high='No existe esa banda' if rate==11025 else f'{r["high_band_db_relative"]:.1f} dB'
    rate_rows.append(f'| {kind}, {rate} Hz | {r["duration_s"]:.3f} s | {r["rms"]:.1f} | {20*math.log10(r["rms"]/base):+.2f} dB | {high} |')
fric_rows=[]
for lang,word in [('eses','asa'),('esus','asa'),('eses','aza')]:
    r=records[f'{lang}_{word}_11025.wav'];f=r['frication'];t=r['frication_targets']
    fric_rows.append(f'| {lang}, {word} | {f["centroid_500_5000_hz"]:.0f} Hz | {f["rms"]:.0f} | {t["f3"]:.0f}, {t["f4"]:.0f}, {t["f5"]:.0f} Hz | {t["a3f"]:.0f}, {t["a4f"]:.0f}, {t["a5f"]:.0f}; AB={t["ab"]:.0f} |')
wide=json.loads((OUT/'wide-validacion.json').read_text(encoding='utf8'))
report='''# Auditoría del núcleo Klatt y del español

Fecha: 6 de octubre de 2026.

## Conclusiones para decidir el siguiente paso

1. La base vocal de ambas variantes es muy parecida: las cinco vocales comparten prácticamente los mismos objetivos de formantes y anchos de banda.
2. La /s/ latinoamericana tiene una configuración de ruido distinta, más concentrada en las resonancias altas. Es una explicación acústica plausible del timbre que te molesta. Las muestras examinadas no muestran sustitución de /s/ por la /θ/ castellana.
3. El remuestreo sinc conserva el sonido y el nivel originales. La síntesis directa a 24 y 32 kHz ya funciona en este corpus, pero cambia el nivel, algunos detalles espectrales y la duración por redondeo. No demuestra por sí sola mejor calidad.
4. Hay un fallo confirmado en el selector de aritmética experimental y una discrepancia residual después de corregirlo en una copia. Conviene resolverlos antes de activar esa ruta para Android.
5. Hay otros puntos programáticos concretos que requieren pruebas específicas: separación del estado del ruido, límites de estabilidad a 8 kHz, validación de parámetros y aislamiento de estados por instancia.

Los APK 0.2 y las reglas del motor se mantienen como estaban. Las correcciones ensayadas viven únicamente en una compilación de investigación.

## Qué se revisó y cómo se comprobó

Se usó el núcleo incluido en el port Android, revisión `520585138206f1692c619ae2b45856cbbe4255b3`, y se compiló una versión Windows x86_64 con LLVM MinGW. Se mantuvo `-fsigned-char`, como en Android. La comparación corresponde a ese código: no a una versión posterior del repositorio ni a una ejecución en un teléfono.

Se generaron 56 WAV originales: frases en ambas variantes a seis frecuencias, remuestreo sinc a 24/32 kHz, ocho contextos consonánticos por variante, cinco vocales por variante a 11.025 Hz, cinco vocales castellanas adicionales a 24/32 kHz y cuatro ensayos de ruido/aritmética. Cada síntesis se ejecutó en un proceso nuevo, con límite de 25 segundos. Todas finalizaron con código 0.

La voz 1 coincide en los dos módulos: género 0, tamaño 50, tono 65, fluctuación 30, rugosidad 0, aire 0, velocidad 50 y volumen 92. El texto se entregó como CP1252, que es lo que espera esta entrada del motor. No se usaron ecualizador ni limitador del port Android.

Se capturaron los 62 parámetros que recibe `KlattSynth`, se midieron PCM de 16 bits, energía espectral, nivel RMS, muestras en los extremos y duración. El corpus no presentó muestras exactamente en -32768/32767; eso no prueba ausencia de desbordamientos internos. No reprodujo los cuelgues anteriores de Android, que requieren otro corpus y el dispositivo.

La frase utilizada fue: «Susana sale de su casa. Cecilia caza cerca del cerro. La lluvia moja la calle. Mi voz pronuncia a, e, i, o, u.»

Los archivos `.run.json` registran texto, comando, entorno experimental y salida. `resultados.json` contiene las mediciones y hashes; `resumen.csv` ofrece una tabla pequeña. Los `.txt` de entrada están en CP1252; el informe y JSON están en UTF-8.

## Cómo produce el sonido

El flujo es: texto → reglas Delta y pronunciación → trayectorias temporales → cuadros Klatt → fuente de voz y ruido → resonadores → PCM → remuestreo opcional.

Las vocales se producen principalmente con voz que atraviesa resonadores en cascada. Las fricativas emplean ruido y resonadores paralelos con amplitudes independientes; también existe una salida de ruido sin esos resonadores, `AB`. Por eso una /s/ requiere revisar tanto frecuencias como amplitudes y anchos de banda. Este diseño concuerda con el modelo descrito por [Klatt, 1980](https://www.fon.hum.uva.nl/david/ma_ssp/doc/Klatt-1980-JAS000971.pdf). El artículo propone valores para inglés; no los tomé como objetivos normativos del español.

El motor admite hasta ocho formantes y configura cinco por defecto, salvo que la llamada indique otro número. Que F6, F7 o F8 aparezcan en la captura no demuestra que estén activos. Además, toda frecuencia consultada se limita a 5.000 Hz: F7=6.300 y F8=7.500 no pueden conservar esos valores si se activan por esta ruta. Debe registrarse también `cp.n_formants` antes de intentar ampliarla.

### Parámetros que importan lingüísticamente

| Familia | Parámetros | Qué conviene controlar |
|---|---|---|
| Melodía y fuente | F0, AV, OQ, TL, FL, DI, AH | Entonación, intensidad, apertura glotal, inclinación espectral, irregularidad y aspiración |
| Resonancia vocal | F1–F8, B1–B8, DF1, DB1 | Identidad vocálica, anchura de las resonancias y ajustes asociados a la fase glotal |
| Fricación | AF, A1F–A8F, B1F–B8F, AB | Lugar acústico y distribución del ruido de /s/, /θ/, /x/, /f/ y explosiones |
| Nasalidad | FNP, BNP, FNZ, BNZ | Resonancia y antirresonancia nasal |
| Acoplamiento traqueal | FTP, BTP, FTZ, BTZ | Polos y ceros adicionales de la fuente/trato vocal |
| Tiempo | step y duración del flujo | Transiciones, duración de segmentos, pausas y coordinación del gesto acústico |

F0 se registra en décimas de Hz: un valor 1118 representa 111,8 Hz. Los formantes y sus anchos se expresan en Hz. Las amplitudes son controles internos en dB, no medidas de presión sonora calibradas.

En esta frase cambiaron 23 parámetros castellanos y 22 latinoamericanos. El cero nasal FNZ recorrió 200–350 Hz y el polo FNP se mantuvo en 200 Hz: aquí sí hay activación del par nasal. Los pares traqueales coinciden a 250 Hz y sus anchos coinciden, por lo que se cancelan. Las amplitudes de voz paralela permanecieron en cero. Son observaciones de este corpus, no garantías para todos los textos y voces.

## Coeficientes: resultados de la auditoría

Para un resonador de frecuencia F, anchura BW y frecuencia de muestreo Fs:

```
r = exp(-π BW / Fs)
θ = 2π F / Fs
B ≈ 2r cos(θ)
C ≈ -r²
A ≈ 1 - B - C
y[n] = A x[n] + B y[n-1] + C y[n-2]
```

El código guarda `sa`, `sb` y `sc` en escalas diferentes y los recupera como A=4sa/32768, B=2sb/32768 y C=sc/32768. Aplica truncamiento y borra bits bajos; usar sus enteros como si fueran coeficientes flotantes sin estos factores daría un filtro distinto.

La generación reproduce las 3.991 entradas de decaimiento de cada tabla original de 8.000 y 11.025 Hz. Reproduce todas las entradas de coseno de 11.025 Hz. A 8.000 Hz hay dos diferencias de una unidad: 3.992 y 4.008 Hz, donde la tabla original usa -32768 y la fórmula redondeada -32767. El arnés C existente confirmó esas mismas dos diferencias. Después su control de duración falló porque contiene una referencia inglesa fija y esta compilación sólo incluye español: ese fallo no valida ni invalida la acústica española.

Se comprobaron exhaustivamente 19.919.081 combinaciones de F=10…5.000 y BW=10…4.000 por frecuencia, usando los coeficientes enteros y las condiciones de Jury de segundo orden. A 11.025, 22.050, 24.000, 32.000 y 44.100 Hz no apareció ningún par estacionario fuera de la estabilidad estricta. Esto no cubre cambios de coeficientes durante las transiciones, saturaciones ni la suma de filtros.

A 8.000 Hz aparecieron 100 combinaciones con margen cero: estabilidad marginal, no necesariamente explosión de amplitud. Un ejemplo es F=3.988 Hz y BW=11 Hz, muy cerca de Nyquist. No se ha demostrado que las reglas españolas lleguen a esos valores. Es un límite relevante si se desbloquean parámetros extremos. La banda superior a 4.000 Hz tampoco puede representarse sin aliasing a esa frecuencia.

La cuantización empeora a frecuencias altas. Para F=500 y BW=60, el ángulo del polo queda en 500,06 Hz a 11.025; 500,77 a 24.000; 504,09 a 32.000 y 508,83 a 44.100. El ancho efectivo queda aproximadamente entre 59,37 y 59,99 Hz. El ángulo del polo no equivale exactamente al máximo del espectro.

## Vocales españolas: objetivo frente al audio

Se usó la anotación de vocal aislada y acentuada, por ejemplo `` `[.1a] ``. La tabla muestra medianas de los cuadros sonoros. La estimación acústica usa LPC de orden 12, ventanas centrales de 40 ms y preénfasis 0,97; los polos se emparejan con los objetivos conocidos. Es una comprobación orientada por esos objetivos, no una estimación ciega ni un valor normativo de hablantes humanos.

| Vocal | F1, F2, F3 ordenados (Hz) | F1, F2, F3 estimados del PCM a 11.025 Hz |
|---|---|---|
'''+'\n'.join(vowel_rows)+'''

Los anchos B1/B2/B3 son 120/100/150 Hz en estas vocales. La variante latinoamericana comparte prácticamente esos objetivos; /a/ y /u/ presentan pequeños cambios temporales en F2/F3 y hay diferencias de F0/duración. Para dialectalizar conviene conservar la organización de cinco vocales como punto de partida y medir los ajustes con hablantes de cada variedad, incluyendo contextos y acentos.

A 24/32 kHz se mantuvieron los objetivos capturados. La estimación acústica a esas frecuencias se hizo después de reducir sólo la señal de análisis a 11.025 Hz con filtro antialias, para mantener el mismo orden y banda de LPC. Aparecen diferencias de decenas de Hz en algunos polos estimados; esas diferencias incluyen sesgo del estimador, ruido, fuente y aritmética. No equivalen por sí solas a un desplazamiento real idéntico de los formantes.

## La /s/ que te molesta

`st_symb.dr` selecciona `break_into_cast_phones` o `break_into_mex_phones`. En `ss_val.dr` hay reglas distintas, `cast_ph_s` y `mex_ph_s`. La segunda contiene caminos hacia `span_dental_Fv` y `span_ph_z`, además de cambios contextuales de amplitud. Un nombre de regla no es una transcripción fonética segura.

En el tramo central de «asa», seleccionado por AF>0 y AV=0 y recortando 10 ms en ambos extremos, se obtuvo:

| Muestra | Centroide del ruido entre 500 y 5.000 Hz | RMS del tramo (cuentas PCM) | F3/F4/F5 enviados | A3F/A4F/A5F y AB enviados |
|---|---|---|---|---|
'''+'\n'.join(fric_rows)+'''

En la latinoamericana, «asa» y «aza» son idénticas byte por byte; también lo son «casa» y «caza». En castellano se distinguen. Esto respalda el seseo en esos contextos. La /s/ latinoamericana concentra el ruido en resonancias superiores, mientras que la /θ/ castellana usa una configuración distinta y más débil. El espectro latinoamericano no coincide con la /θ/ castellana examinada.

La hipótesis útil es un perfil de sibilancia excesivamente agudo o concentrado para tu preferencia. No podemos inferir elevación del labio, posición interdental o palatalización articulatoria exacta a partir de estas muestras. Tampoco hemos comparado una grabación de tu versión Android o de ETI Eloquence: no se puede extrapolar este resultado a todas las versiones.

![Comparación del ruido: /s/ latinoamericana alrededor de 4,6 kHz; /s/ castellana alrededor de 3,6 kHz; /θ/ castellana con menor concentración alta.](comparacion-fricativas.png)

Para ajustarla, conviene probar F4/F5, B4F/B5F, A4F/A5F y AB antes de alterar la correspondencia entre letras y fonemas. Bajar AF reduce el ruido global, pero no corrige por sí solo su distribución. Un ecualizador global también modifica vocales, /tʃ/ y otras consonantes; para dialectalización es preferible un ajuste localizado en el fonema y su contexto.

## Frecuencia de muestreo y resultado acústico

| Ruta castellana | Duración | RMS (cuentas PCM) | Cambio de nivel respecto a 11.025 | Energía por encima de 5.512,5 Hz / total |
|---|---|---|---|---|
'''+'\n'.join(rate_rows)+'''

El remuestreo sinc produce un nivel prácticamente idéntico y una banda superior muy débil. Esa banda débil es residuo del filtro, no información fonética recuperada. La síntesis directa genera muestras nuevas, pero su amplitud cae aproximadamente con la frecuencia de muestreo. La normalización de la fuente glotal contiene una división por Fs y es un punto para investigar; no se ha demostrado que sea la única causa.

Hay un filtro de ruido Butterworth de cuarto orden a 5.512 Hz en la ruta directa superior a 11.025. Al desactivarlo con `EVV_NOISE=flat`, la energía alta de la frase castellana a 32 kHz sube de -37,3 a -28,3 dB respecto al total. Tener más energía aguda no acredita una mejor /s/: puede ser sólo ruido adicional.

La duración a 24/32 kHz aumenta unos 19 ms en esta frase. `n_samples` trunca cada cuadro por separado: 5 ms a 11.025 Hz son 55,125 muestras, de las que se emiten 55; a 24/32 kHz la división resulta entera. Un acumulador de fracciones de muestra podría corregir el tiempo, pero alteraría la salida histórica. Se recomienda una ruta nueva con opción explícita y comparación auditiva.

![Espectros medios de la misma frase en ambas variantes, con niveles originales y comparación de síntesis directa y remuestreo.](comparacion-espectros.png)

## Fallos y mejoras programáticas concretas

### 1. Selector experimental WIDE: fallo confirmado

`klatt_fx.c` usa `wide_on = allowed && rate > 11025;`. El operador lógico convierte tanto `allowed=1` como `allowed=2` en 1. El bloque que comprueba `klatt_wide_on()==2` queda inaccesible. Las muestras de `on` y `emulate` originales fueron idénticas.

Se compiló una copia de investigación con:

```c
wide_on = rate > 11025 ? allowed : 0;
```

Ahora `emulate` sí entra en su ruta. Sin embargo, frente al cálculo normal, difieren '''+str(wide['differing_samples'])+''' de '''+str(wide['original_samples'])+''' muestras; diferencia máxima '''+str(int(wide['max_sample_difference']))+''' cuentas y RMS de diferencia '''+f'{wide["rms_sample_difference"]:.3f}'+''' cuentas. Esta reproducción confirma una discrepancia estructural residual. El comentario del código propone revisar el traspaso de historia cuando el bloque tiene cero o una muestra; es una hipótesis por localizar. No conviene activar WIDE por defecto todavía.

### 2. Estado del ruido: selección potencialmente incorrecta

`noise()` actualiza `seed` con `klatt_rand()` antes de compararla con `k->unknown_19dc` para elegir el estado del filtro de aspiración o fricación. Está comparando la semilla nueva con la anterior. Eso no identifica de forma fiable la fuente que llamó a la función. Se recomienda pasar un identificador explícito de fuente o escogerlo antes de actualizar la semilla. Falta un experimento dedicado para cuantificar cuánto modifica el sonido y confirmar la ruta completa.

### 3. Estados globales y concurrencia

Los estados de ruido (`shaper`, 8 entradas) y los estados WIDE (`wide`, 64 entradas) son arreglos globales identificados por punteros, con reemplazo de la primera entrada si se llenan. No hay liberación evidente al destruir el sintetizador. Conviene llevarlos al contexto de cada instancia y reiniciarlos al abrir/cerrar. Es especialmente relevante para alternar voces y para futuras sesiones simultáneas dentro de un proceso. Dos APK separados tienen espacios de memoria separados.

### 4. División fija y parámetros extremos

`fxdivl()` contiene un bucle de normalización que no termina si `num` es positivo, menor que `den` y sus 16 bits bajos son cero. El propio comentario documenta ese caso. Ejemplo aritmético: num=65536 y den=131072. No se ejecutó ese caso deliberadamente ni se ha demostrado que un texto del corpus lo provoque. Debe corregirse y probarse con un cálculo ancho o una normalización que preserve todos los bits antes de usar parámetros sin límites.

Además, las ganancias y sumas de resonadores usan enteros de 32 bits. Que los polos sean estables no impide desbordar un acumulador. Las transiciones incluyen desplazamientos de enteros con signo. Conviene instrumentar máximos internos, conversiones y saturaciones, y ejecutar sanitizadores compatibles en una compilación de investigación. Una salida PCM sin recorte no es evidencia suficiente de seguridad interna.

### 5. Rediseño controlado para 24/32 kHz

- Coeficientes en doble precisión o con mayor precisión fija, estados por instancia y comparación con una referencia del mismo algoritmo.
- Fase glotal y duración con acumuladores fraccionarios; revisar normalización de fuente y ganancia del ruido.
- Límites de formantes coherentes con Nyquist y margen antialias; ampliar tablas por encima de 5 kHz sólo al ampliar también los objetivos lingüísticos.
- Interpolación temporal de coeficientes basada en tiempo. La rampa actual de tres muestras dura unos 0,27 ms a 11.025 y 0,094 ms a 32.000; cambia al cambiar Fs.
- Medir fricativas, explosiones, nasalidad y voz aireada además de vocales. La configuración de TL usa tablas originales en 8/11,025 kHz y otra construcción en las frecuencias nuevas.
- Separar parámetros de interfaz, estilo de voz, variante lingüística y detalles numéricos del renderizador.

## Cómo modernizar las reglas lingüísticas

Una capa legible de configuración debería nombrar fonema, rasgo y contexto, por ejemplo: /s/, posición final o intervocálica, vocal vecina, énfasis y variedad. Esa capa generaría los objetivos de las reglas actuales sin editar manualmente sus desplazamientos numéricos.

| Nivel | Adición propuesta | Datos necesarios |
|---|---|---|
| Fonología | Distinción/seseo, yeísmo y realización de /x/, reducción o conservación de /s/ final | Inventario y condiciones de cada variedad concreta |
| Fonética | Perfiles de ruido por consonante, transiciones, duración, sonorización y aspiración | Grabaciones etiquetadas de varios hablantes y contextos |
| Prosodia | Acento, grupos entonativos, interrogación, foco, continuaciones y límites de frase | Frases leídas con intenciones y estructuras comparables |
| Pragmática | Perfiles explícitos de aviso, lectura continua y énfasis solicitado | Función del texto; no inferir automáticamente emociones ambiguas |
| Normalización | Unicode, emoji, abreviaturas, números, siglas y mayúsculas | Corpus real de Android y registros de fallos |

No conviene tratar toda América como una única variedad. Las zonas geográficas servirían como nombres de perfiles, pero las decisiones deben expresarse mediante rasgos medidos. Por ejemplo, un perfil puede ajustar la /s/ final sin alterar la /s/ inicial. Esa separación también permite que tú revises las decisiones lingüísticas sin programar.

La primera modificación perceptiva que recomiendo es ofrecer varias configuraciones localizadas de /s/, manteniendo el resto de la voz. Deben compararse con etiquetas ocultas y volumen comparable, para evitar elegir simplemente la más fuerte. Después se puede construir un perfil regional y trabajar prosodia. La equivalencia digital por sí sola no valida naturalidad, inteligibilidad o preferencia.

## Material para escuchar y reproducir

Abrir [galería accesible de audio](escucha.html). Ofrece comparaciones a nivel RMS igualado para las frecuencias de muestreo y los originales de «asa»/«aza»; no reproduce nada automáticamente. La normalización sólo modifica las copias terminadas en `_escucha.wav`. Los originales medidos se conservan.

- [Datos completos](resultados.json).
- [Tabla resumida](resumen.csv).
- [Validación de la corrección WIDE](wide-validacion.json).
- [Factores de normalización para escucha](normalizacion-escucha.json).
- [Compilación de investigación](../../tools/build-acoustic-host.ps1).
- [Generación y medición](../../tools/acoustic-audit.py).
- [Experimento WIDE aislado](../../tools/acoustic-wide-check.py).
- [Generación de este informe](../../tools/acoustic-report.py).

Para reproducir: compilar con el script PowerShell; ejecutar `acoustic-audit.py --generate`; ejecutar `acoustic-wide-check.py`; ejecutar `acoustic-audit.py --analyze`; ejecutar `acoustic-report.py`. Se necesitan LLVM MinGW, GNU Make/Bash y Python con NumPy/SciPy/Matplotlib. En este equipo las dependencias científicas están en `.codex/build-tools/evvdroid/acoustic-python` y se incorporan mediante `PYTHONPATH`.

## Límites de lo comprobado

La revisión es detallada sobre coeficientes, fuente, filtros, captura de parámetros, vocales, fricación y cambio de frecuencia. No es una validación de todas las reglas Delta ni de todos los dialectos españoles. Las mediciones son objetivas; no realicé una valoración auditiva humana. Los archivos permiten que tú compares el timbre. Las preferencias y la inteligibilidad deben comprobarse escuchando, idealmente con más participantes.

Los resultados no certifican ARM32/ARM64, TalkBack, cambios de voz repetidos, llamadas simultáneas o textos con emoji. Tampoco identifican la causa de los cuelgues que experimentaste. Para esos problemas el siguiente corpus debe ejecutarse en Android registrando el texto normalizado, la voz, el último cuadro procesado y el fallo nativo.
'''
(OUT/'INFORME.md').write_text(report,encoding='utf8')
sections=[]
for lang,label in [('eses','Español de España'),('esus','Español latinoamericano')]:
    items=[]
    for kind,rate,title in [('directa',11025,'Referencia, síntesis a 11.025 Hz'),('sinc',32000,'Remuestreo sinc a 32.000 Hz'),('directa',32000,'Síntesis directa experimental a 32.000 Hz')]:
        name=f'{lang}_frase_{kind}_{rate}_escucha.wav';ident=f'{lang}-{kind}-{rate}'
        items.append(f'<section aria-labelledby="{ident}"><h3 id="{ident}">{title}</h3><p>Nivel RMS igualado a 2.000 cuentas PCM. Texto: {html.escape(data["phrase"])}</p><audio controls preload="none" aria-label="{label}: {title}"><source src="{name}" type="audio/wav"></audio><p><a href="{name}">Descargar WAV: {title}</a></p></section>')
    for word,title in [('asa','asa: consonante escrita s'),('aza','aza: consonante escrita z')]:
        name=f'{lang}_{word}_11025.wav';ident=f'{lang}-{word}'
        items.append(f'<section aria-labelledby="{ident}"><h3 id="{ident}">{title}</h3><p>Audio original, 11.025 Hz. Sin igualación de nivel.</p><audio controls preload="none" aria-label="{label}, {word}"><source src="{name}" type="audio/wav"></audio><p><a href="{name}">Descargar {label}, {word}</a></p></section>')
    sections.append('<h2>'+label+'</h2>'+''.join(items))
page='''<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Comparaciones acústicas de OpenEVV</title><style>body{font:1.15rem system-ui;line-height:1.6;max-width:58rem;margin:auto;padding:1.5rem;background:#fff;color:#111}a{color:#124cb3}section{border:1px solid #666;padding:1rem;margin:1rem 0}audio{width:100%}a:focus-visible,audio:focus-visible{outline:4px solid #124cb3;outline-offset:4px}</style><main><h1>Comparaciones acústicas de OpenEVV</h1><p>Los reproductores no se inician solos. Navega por encabezados para elegir variante y muestra. Las frases tienen el nivel RMS igualado para comparar el timbre; las palabras conservan su nivel original.</p><p>La síntesis directa superior a 11.025 Hz es experimental. Las muestras se generaron en Windows con el mismo código nativo del APK 0.2.</p><p><a href="INFORME.md">Leer el informe completo</a> · <a href="resultados.json">Descargar los datos de medición</a></p>'''+''.join(sections)+'''<h2>Qué comparar</h2><p>En las frases, compara claridad, aspereza y ruido entre las tres rutas. En las palabras, compara la s de asa entre variantes y después asa con aza dentro de la misma variante. En la latinoamericana los dos archivos son digitalmente idénticos.</p></main></html>'''
(OUT/'escucha.html').write_text(page,encoding='utf8')
print('Informe y galería escritos en',OUT)
