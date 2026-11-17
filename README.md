# Asalariado

App Android para llevar el control de tus **turnos, horas y salario** y comprobar que cobras lo que te corresponde.

## Qué hace

- **Calendario de turnos** (Mañana, Tarde, Noche, Partido, Libre, Vacaciones, Festivo, Asuntos propios, Baja).
  - Toca la columna **Sem** de una semana para marcarla entera de mañana, tarde, noche…
  - Toca un día para cambiarlo: otro turno, un horario distinto, festivo trabajado o una nota.
  - Botón **Rotación**: elige el ciclo (Mañana → Tarde, Mañana → Tarde → Noche…), di qué turno haces
    una semana concreta y rellena el calendario hasta la fecha que quieras. Los días que hayas
    cambiado a mano o importado se respetan, salvo que marques «Sobrescribir».
- **Subir captura del horario**: eliges una o varias capturas (o usas «Compartir» desde la galería o WhatsApp)
  y la app lee el texto **en el propio móvil** (ML Kit, sin internet). Detecta:
  - listas tipo `Lunes 3 Mañana`, `04/11 14:00-22:00`, `6 nov Noche`;
  - tablas/cuadrantes con una fila de días y otra de turnos (`M`, `T`, `N`, `L`, `D`, `V`, `F`).
  Antes de guardar puedes revisar y corregir cada día.
- **Calendario laboral anual por colores** (como el de convenio de 12 meses): sube la captura
  y la app localiza cada mes, encaja la cuadrícula con los números que lee y mira el **color** de cada casilla:
  rojo = domingo/festivo, verde = sábado/F.R., azul = vacaciones, amarillo = jornada de 6 h,
  morado = activable laborable, rosa = activable F.R., celeste/verde claro = puente.
  Calcula las horas de cada mes y la jornada anual (restando los días de libre disposición),
  y la rotación de mañana/tarde respeta festivos, vacaciones y jornadas cortas.
  Si te activan un sábado o festivo recuperable, lo marcas con su turno (8 h): cuenta como activable
  (máximo obligatorio configurable, 5 por defecto) y se paga al precio de sábado.
- **Médico**: apunta citas con hora de inicio y fin, si tienes justificante, y ves las horas usadas frente a la bolsa anual.
- **Nómina**: para cada mes calcula el bruto esperado (con pagas extra, plus de nocturnidad y festivos)
  y un neto aproximado (IRPF y Seguridad Social). Apuntas lo que pone tu nómina y te marca la diferencia.
  El resumen anual compara las horas del calendario con las horas de convenio y avisa del exceso de jornada.
- **Condiciones**: salario bruto anual, pagas (12/14/15, prorrateadas o no), horas anuales, días laborables,
  horario de cada turno, pluses, precio de la hora extra, vacaciones, asuntos propios y horas de médico.

Todos los datos se guardan sólo en el móvil.

## Instalar el APK

Cada push compila el APK en GitHub Actions (pestaña **Actions** → último «Android APK» → artefacto
`Asalariado-apk`). Descomprime el zip, pasa `Asalariado.apk` al móvil y ábrelo
(hay que permitir «instalar apps de origen desconocido»).

## Compilar en local

Requiere JDK 17 y el Android SDK (Android Studio):

```bash
./gradlew :app:assembleRelease      # APK en app/build/outputs/apk/release/
./gradlew :core:test                # tests de la lógica (rotación, nómina, lectura de capturas)
```

## Estructura

- `core/` – lógica en Kotlin puro y testeada: modelo, rotación de turnos, cálculo de nómina y
  el intérprete de capturas (`ScheduleParser`).
- `app/` – app Android con Jetpack Compose, Room y ML Kit Text Recognition.
