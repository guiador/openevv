# Segunda escucha: fricción, naturalidad y velocidad habitual

El usuario evaluó `bv-tuning-01` y precisó que su velocidad habitual es **250 palabras por minuto**. Las observaciones anteriores a 180 y 400 son valores configurados del motor; no hay aún una evaluación a 250.

| Palabra | Fricción reforzada | Fricción y duración ampliada | Observación a 400 |
|---|---|---|---|
| vaca | Se distingue de forma parecida, pero falta la sensación de vibración labiodental natural; suena ensordecida | Suena antinatural | Cree que se nota, con reserva porque no escucha habitualmente a esa velocidad |
| votar | A 180 suena normal | A 180 cambia poco salvo cierto alargamiento percibido | Con duración ampliada se nota la articulación labiodental |
| tuvo | A 180 se nota la articulación labiodental | También se nota, pero la v resulta algo alargada | No nota el contraste |

La referencia provisional para la siguiente prueba es **fricción reforzada con duración original**. La opción alargada no se adopta como base por sus problemas de naturalidad, aunque se conserva la observación favorable de votar a 400. Las observaciones a 400 no constituyen una evaluación por un oyente habituado a esa velocidad.

## Qué puede afirmarse sobre el remuestreo

El generador fija la salida en 11.025 Hz después de registrar el buffer, usa la voz estrecha del motor y limpia las variables de ajuste `EVV_*` del proceso hijo. Esta es la frecuencia base del sintetizador en la prueba; no se añadió una etapa para elevar o reducir su frecuencia de salida. Esto descarta el remuestreo añadido como explicación de estas muestras. No demuestra que la limitación de banda, la fuente glotal, las transiciones o la mezcla de sonoridad y ruido sean perceptivamente adecuadas.

## Siguiente comparación: solo sonoridad, a 250

La sensación de ensordecimiento sugiere probar el equilibrio entre la excitación sonora y la fricación. Es una hipótesis de trabajo, no un diagnóstico acústico definitivo. Se mantienen el valor de fricación 52, los formantes y los valores originales de duración 50/60. Solo se modifican las dos asignaciones del slot 2754 de `span_ph_v`:

| Perfil | Valores de sonoridad internos |
|---|---|
| friction, referencia | 45 / 50 |
| friction-voice3 | 48 / 53 |
| friction-voice6 | 51 / 56 |

No son decibelios acústicos medidos. El trazado debe mostrar cambios exclusivamente en AV, sin cambios de duración, AF, frecuencia fundamental ni formantes. Se verifican las tramas completas, no solo sus máximos. También se comprueba que la nueva compilación de referencia reproduzca la DLL de fricción reforzada anterior a 250.

```powershell
# Generar la referencia a 250 con la DLL de la prueba anterior.
python experiments/espanol-bv/run.py generate --out build/espanol-experimental/referencia-250-nueva --dll build/espanol-experimental/bv-tuning-01/friction.dll --speeds 250

# Crear una carpeta nueva para la comparación de sonoridad.
./experiments/espanol-bv/build-variants.ps1 -Out build/espanol-experimental/sonoridad-nueva -Study voicing -Speeds 250 -Reference C:/ruta/al/repositorio/build/espanol-experimental/referencia-250-nueva/report.json
```

Escuchar si la v recupera una vibración natural, si sigue siendo distinguible y si el incremento provoca exceso de volumen, zumbido o un nuevo artefacto. Si el ajuste no mejora esas cualidades, el siguiente factor a estudiar será el espectro o la evolución temporal; no se seguirá aumentando la sonoridad sin evidencia.

Esta prueba permanece en la copia de laboratorio, con español americano, Windows x64 y reglas interpretadas. La matriz nativa completa y la evaluación de otros oyentes siguen pendientes antes de cualquier integración distribuible.

## Resultado técnico de `bv-voicing-01`

Se generaron 54 WAV, 18 por perfil, sin muestras saturadas. La referencia reconstruida reproduce los 18 audios generados con la DLL anterior a 250. Cada ajuste de sonoridad cambia solo los tres casos ortográficos con v; conserva los otros 15. Todos los audios mantienen el mismo número de muestras que su referencia.

Las nueve trazas coinciden con el PCM de sus audios sin instrumentación. En los dos ajustes se observan cambios únicamente en la columna AV, con aumento durante tramas que contienen fricación; el resto de las columnas y el número de tramas permanecen idénticos. Pasan las 114 comprobaciones de comparación y las seis pruebas automatizadas del banco. La naturalidad y la identificación auditiva de esta nueva comparación siguen pendientes de la escucha del usuario.
