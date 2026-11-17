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
- **Calendario laboral anual por colores** (como el de convenio de 12 meses): en Importar →
  «Calendario anual» eliges directamente el **PDF** o una captura
  y la app localiza cada mes, encaja la cuadrícula con los números que lee y mira el **color** de cada casilla:
  rojo = domingo/festivo, verde = sábado/F.R., azul = vacaciones, amarillo = jornada de 6 h,
  morado = activable laborable, rosa = activable F.R., celeste/verde claro = puente.
  Calcula las horas de cada mes y la jornada anual (restando los días de libre disposición),
  y la rotación de mañana/tarde respeta festivos, vacaciones y jornadas cortas.
  Si te activan un sábado o festivo recuperable, lo marcas con su turno (8 h): cuenta como activable
  (máximo obligatorio configurable, 5 por defecto) y se paga al precio de sábado.
- **Rotación sencilla**: en la columna «Sem» dices «esta semana estoy de mañana» (o de tarde) y las siguientes
  se alternan solas hasta fin de año; «Cambiar sólo esta semana» invierte mañana/tarde de esa semana sin tocar el resto.
- **Médico**: médico general / acompañante (bolsa anual, 16 h por defecto) y especialista propio (sin límite).
  En las horas de médico y los días de libre disposición no se cobra la prima: se descuenta a precio de prima por hora.
- **Pagas extra aparte**: julio (hacia el día 20) y diciembre (hacia el 15), configurables. Cada una tiene su
  recibo y se compara con lo esperado. Se generan por semestre y pierden la parte de los días de permiso por
  nacimiento (lo paga la Seguridad Social) y de huelga.
- **Huelga, paros y asambleas**: día de huelga y horas de paro no se cobran (salario + prima por hora);
  las horas o medias horas de asamblea se cobran sin la prima.
- **Permiso por nacimiento** (paternidad/maternidad): se marca con «Aplicar hasta…»; esos días no los paga la empresa.
- **Escalones**: tabla salarial Bellota 2026 (escalones 9–21): al elegir el escalón se rellenan jornal, prima,
  prima por hora y nocturnidad.
- **Importar nómina** (PDF o captura): lee periodo, escalón, jornal, prima, plus de sábados, descuentos de prima,
  devengos, IRPF, SS, Geroa y líquido; la compara con lo esperado y puede actualizar tus condiciones.
- **Nómina**: para cada mes calcula el bruto esperado (con pagas extra, plus de nocturnidad y festivos)
  y un neto aproximado (IRPF y Seguridad Social). Apuntas lo que pone tu nómina y te marca la diferencia.
  El resumen anual compara las horas del calendario con las horas de convenio y avisa del exceso de jornada.
- **Condiciones**: salario bruto anual, pagas (12/14/15, prorrateadas o no), horas anuales, días laborables,
  horario de cada turno, pluses, precio de la hora extra, vacaciones, asuntos propios y horas de médico.

- **Ajustes** (igual que Baby Registry): temas Lavanda, Océano, Coral, Bosque, Miel, Grafito y Material You;
  modo claro/oscuro; estilos Suave, Burbuja, Minimalista, Geométrico y Píxel; fuentes Nunito, Quicksand,
  Poppins, Lora y Pixelify; fondos; copia de seguridad.

Todos los datos se guardan sólo en el móvil.

## Instalar el APK

Cada push compila el APK en GitHub Actions (pestaña **Actions** → último «Android APK» → artefacto
`Asalariado-apk`). Descomprime el zip, pasa `Asalariado.apk` al móvil y ábrelo
(hay que permitir «instalar apps de origen desconocido»).

## Actualizar sin perder datos (firma)

Para que cada versión se instale encima de la anterior, el APK se firma siempre con la misma clave.
Se guarda en los secretos del repositorio (Settings → Secrets and variables → Actions):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`. Sin ellos, el workflow firma
con una clave temporal y avisa. Además, en **Ajustes → Copia de seguridad** puedes guardar y restaurar
todos los datos en un archivo.

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

## Licencias

Las fuentes (Nunito, Quicksand, Poppins, Lora y Pixelify Sans) se distribuyen con la SIL Open Font License;
ver la carpeta `licenses/`.
