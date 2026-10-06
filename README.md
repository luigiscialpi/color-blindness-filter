# colorfilter

App Android personale (richiede root) che applica una matrice colore a livello di
compositor tramite SurfaceFlinger. Lo sviluppo segue la documentazione tecnica
`filtro-colori-android-design`.

## Build e test

Il wrapper Gradle non è incluso: generarlo con `gradle wrapper --gradle-version 8.13`
oppure aprire il progetto con Android Studio. Poi:

    ./gradlew testDebugUnitTest
