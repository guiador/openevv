# Primera evaluación auditiva y segundo ajuste

El usuario evaluó la corrida `bv-02` y comunicó lo siguiente:

| Palabra | A 180 configuradas | A 400 configuradas |
|---|---|---|
| vaca | Se nota el contraste | Sin observación específica |
| votar | Suena como «wotar» | Suena como «utar» o parecido |
| tuvo | Se nota poco | Pierde la articulación labiodental y se acerca a una b labial |

Se registra como evaluación cualitativa; no hay respuestas de identificación ni porcentajes de acierto para esta escucha.

## Hipótesis y ajustes

La fricación de la v puede resultar demasiado débil o breve en ciertos contextos, especialmente a velocidad alta. La audición descrita no basta para establecer la causa. Para aislar dos factores, la siguiente prueba compara tres perfiles:

| Perfil | Valor inicial del slot 2762 en `span_ph_v` | Valores del slot de duración 2074 en `span_ph_v_dur` |
|---|---:|---:|
| original | 40 | 50 / 60 |
| friction | 52 | 50 / 60 |
| friction-duration | 52 | 80 / 90 |

Son valores internos de las reglas; no se presentan como decibelios medidos ni como duración efectiva garantizada. El trazado de Klatt permite comprobar si aumenta el parámetro AF que llega al sintetizador y cuánto tiempo contienen fricación las tramas. El máximo global de AF no basta para localizar la v, porque los ataques de otras consonantes también pueden producir ruido; el contraste de fricación se comprueba comparando tramas alineadas entre original y friction. La suma de AF ponderada por milisegundos tampoco es una medida de energía acústica.

Las variantes mantienen los objetivos de formantes y la sonoridad de la regla inicial. Esto evita cambiar simultáneamente otros factores; todavía podrían sonar demasiado débiles, excesivas o próximas a f. No se declara corregida la pronunciación antes de escucharlas.

## Reproducción

```powershell
./experiments/espanol-bv/build-variants.ps1 -Out build/espanol-experimental/bv-tuning-nuevo
```

El script crea una copia de las fuentes nativas actuales bajo `build`, compila únicamente español americano con reglas interpretadas para Windows x64 y enlaza tres DLL de laboratorio. Los cambios de reglas se aplican exclusivamente a esa copia. Cada perfil genera el mismo corpus del experimento inicial, con sus controles y páginas de escucha. La comparación final está en `comparar.html`.

`comparison.json` debe confirmar que la compilación original reproduce los 36 audios iniciales, que las dos variantes solo cambian los seis casos ortográficos con v, y que el trazado observa los cambios buscados de fricación y duración. Los WAV instrumentados se comparan con los de la corrida para comprobar que el trazado no altera el PCM. Los perfiles y fuentes de partida quedan registrados con hashes.

La comparación cubre este corpus y esta configuración de laboratorio. No ejecuta la matriz completa de diez idiomas y seis compilaciones, ni constituye una validación de una versión distribuible del motor. Antes de integrar cambios en las fuentes del motor y distribuir una nueva DLL deberán ejecutarse las comprobaciones nativas del repositorio y explicarse los cambios deliberados de los casos afectados.

## Resultado técnico de `bv-tuning-01`

Se generaron 108 WAV sin muestras saturadas: 36 por perfil. La referencia reproduce exactamente el PCM de los 36 casos de `bv-02`. En cada variante ajustada permanecen iguales los 30 casos que no contienen la v ortográfica experimental; cambian los seis casos buscados. Las 120 comprobaciones de comparación pasan. Las 18 trazas se contrastaron con su WAV correspondiente; el trazado no cambió el audio. Se observa más AF en tramas sonoras y, en el perfil con duración ampliada, mayor tiempo total con fricación en las palabras examinadas. Esta última medida abarca la palabra completa, incluidos otros ataques consonánticos, no una segmentación exacta de v.

También pasan cuatro pruebas automatizadas del protocolo, las respuestas y la aplicación restringida de los ajustes. La evaluación auditiva de estas dos variantes está pendiente.

## Próxima escucha

Comparar especialmente votar a ambas velocidades y tuvo a 400. Anotar si aparece una v reconocible, si se conserva la vocal o, si hay un sonido próximo a f y si la mayor duración entorpece la lectura. Si ambas variantes fallan, revisar la coarticulación y la trayectoria temporal; aumentar más la amplitud por sí solo no se considera una solución demostrada.
