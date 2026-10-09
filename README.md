Task relativo: nessun ticket — progetto personale (uso proprio, dispositivo con root)

Stato del documento: **DRAFT, versione 8** — aggiornato dopo lo spike A (matrice colore via SurfaceFlinger), la scelta di Magisk per il boot, la ricerca sugli strumenti di misura, il comportamento osservato di `service call`, il completamento del branch 3, la UI e lo script di avvio. Le parti ancora aperte sono marcate `TO DO`, `DRAFT` o `Da approfondire`.

- Descrizione
- Analisi
  - Filtro ottico e trasformazione su schermo
  - Overlay a blending (alpha)
  - Correzione colore di sistema
  - Matrice colore via SurfaceFlinger (root)
  - Cattura con MediaProjection (differita)
  - Modello della percezione e variante a luminanza
  - Misura oggettiva della condizione
  - Sintesi degli approcci
- Stato attuale
  - Dispositivo di test
  - Esiti dello spike A
  - Cosa resta non verificato
  - Riferimenti tecnici consultati
- DRAFT: Ipotesi di funzionamento
- Principi di sviluppo
- Idee di sviluppo
- Funzionalità da finalizzare
  - MVP: applicazione e controllo del filtro
  - Riapplicazione al boot
  - Fasi successive (differite)
  - Valutazione dell'efficacia
- Sviluppo
  - Rationale
  - Implementazione
  - Checklist
- Elenco file impattati

---

## Descrizione

Lo scopo di questo documento è definire come realizzare un'app Android personale che applichi una trasformazione dei colori all'intero schermo, tarata sulla percezione cromatica di un singolo utente (lo sviluppatore stesso), e fissare le regole con cui l'app va sviluppata. Rispetto alla versione 1, lo spike A ha dimostrato che il percorso primario non è più la cattura dello schermo con `MediaProjection`, ma l'applicazione di una **matrice colore a livello di compositor** tramite root (vedi *Matrice colore via SurfaceFlinger (root)*). Il percorso con `MediaProjection` resta documentato come alternativa differita.

**Profilo dell'utente target (dichiarato, non clinico).** Il test EnChroma eseguito online indica una deuteranomalia marcata ("strong deutan"): coni S 100%, coni L 75%, coni M 0%.

Nota: il valore M 0% va letto con cautela. Il test gira su un display digitale, con i limiti di gamma dinamica e calibrazione che ne derivano, e l'utente percepisce comunque alcune sfumature di verde nella vita reale. La verifica clinica oggettiva (ad esempio Farnsworth D-15 o anomaloscopio) resta da fare e non è sostituita da questo progetto.

**Cosa cambia per l'utente finale.** Lo schermo intero, barre di sistema e tastiera comprese, viene mostrato attraverso una trasformazione lineare dei colori. L'app permette di attivarla, disattivarla e regolarne l'intensità, e la riapplica al riavvio del telefono.

Nota: l'app funziona solo su dispositivi con root e la distribuzione resta personale (sideload). Non è prevista la pubblicazione su store.

## Analisi

Per fissare l'architettura vanno considerati quattro fattori: la differenza tra filtro ottico e trasformazione su schermo, i limiti di un overlay, ciò che Android offre già di serie, e il meccanismo con cui applicare una matrice arbitraria (compositor con root oppure cattura dello schermo). A questi si aggiunge il modello della percezione su cui basare la matrice. Ogni sottosezione presenta Vantaggi e Limiti; la tabella finale li confronta (vedi *Sintesi degli approcci*).

### Filtro ottico e trasformazione su schermo

Un filtro ottico (ad esempio lenti con filtro a banda stretta) agisce sulla luce che arriva all'occhio: attenua per bande di lunghezze d'onda, con una trasmittanza T(λ) che moltiplica lo spettro incidente. Il principio dichiarato è ridurre la sovrapposizione tra le sensibilità dei coni M e L. L'effetto è quindi sottrattivo e per banda, non una somma di lunghezze d'onda; resta corretta l'osservazione di fondo, cioè che a questo problema serve qualcosa di diverso da una semplice sovrapposizione di colore.

Uno schermo non espone lo spettro: emette tre primari con spettro fisso e il software governa soltanto tre intensità (R, G, B) per pixel. Un filtro spettrale a banda stretta non è quindi replicabile via software. Ciò che è realizzabile è ricodificare l'informazione cromatica, sostituendo a ogni pixel un triplet RGB che renda più discriminabile la distinzione rosso-verde. Tecnicamente è una **trasformazione colore per pixel**, non un filtro in senso ottico.

Nota: nel resto del documento "filtro" indica questa trasformazione per pixel.

### Overlay a blending (alpha)

Un overlay (`TYPE_APPLICATION_OVERLAY`, permesso `SYSTEM_ALERT_WINDOW`) viene combinato dal compositor con il contenuto sottostante come `out = α·overlay + (1−α)·contenuto`, canale per canale. L'effetto equivale a una scala più un offset indipendenti per ciascun canale, senza termini incrociati tra canali.

- Vantaggi: nessuna cattura, nessun consenso per sessione, overhead praticamente nullo.
- Limiti: non può eseguire alcuna ricodifica che mescoli i canali; può solo virare o attenuare i colori, riducendo il contrasto; non copre barre di sistema e tastiera.

Esito: scartato.

### Correzione colore di sistema

Android integra una correzione colore con modalità dedicata alla deuteranomalia, controllata tramite `Settings.Secure` (`accessibility_display_daltonizer_enabled`, `accessibility_display_daltonizer`) e applicata a livello di display.

- Vantaggi: overhead nullo, applicata a tutto il sistema, nessuna cattura.
- Limiti: modalità predefinite a parametri fissi; nessuna matrice personalizzata; nessuna calibrazione per utente.

Ruolo nel progetto: **baseline di confronto** (vedi *Valutazione dell'efficacia*).

Nota: nomi delle chiavi e valori vanno verificati sulla versione di Android del dispositivo. Da approfondire.

### Matrice colore via SurfaceFlinger (root)

Il compositor di Android accetta una transazione dedicata, la **1015**, che applica una matrice colore 4x4 all'intero output composto. È la stessa transazione che il sistema usa per Night Light e per la correzione colore; le transazioni vicine sono la 1014 (daltonizer) e la 1022 (saturazione). Da shell root il comando ha la forma:

```
service call SurfaceFlinger 1015 i32 1 <16 valori float>
service call SurfaceFlinger 1015 i32 0        # ripristino
```

La matrice viene inviata in ordine **column-major** e l'ultima riga deve essere (0, 0, 0, 1); in caso contrario il comando non segnala alcun errore (vedi *Cosa resta non verificato*). Per una matrice 3x3 `C` (righe R', G', B' espresse in funzione di R, G, B) la sequenza è: colonna 0 (`C00, C10, C20, 0`), colonna 1, colonna 2, poi `0, 0, 0, 1`.

Esempio: la matrice con `R' = G`, `G' = G`, `B' = B` si invia come `f 0 f 0 f 0 f 0 f 1 f 1 f 0 f 0 f 0 f 0 f 1 f 0 f 0 f 0 f 0 f 1`. Applicata a un rosso puro lo rende nero se la matrice è letta per colonne, verde se è letta per righe: è il test `asym` eseguito sul dispositivo.

- Vantaggi: nessuna cattura, quindi nessun effetto "specchio", latenza e consumo trascurabili; agisce a livello di compositor e, nelle prove, ha filtrato anche tastiera e barra di stato; implementazione minima (un comando).
- Limiti: la transazione non è un'API pubblica e può cambiare tra versioni di Android o di MIUI; la trasformazione è lineare (3x3 più offset), senza LUT 3D né mappe non lineari; richiede root; lo spazio colore in cui la matrice viene applicata (lineare o con gamma) non è verificato; il sistema potrebbe sovrascrivere la matrice in alcuni eventi (non osservato nelle prove).

Nota: il test simmetrico di scambio R/G non basta a verificare l'ordine delle colonne, perché la matrice di scambio coincide con la sua trasposta. Serve una matrice asimmetrica come `asym`.

Esito: **percorso scelto**.

### Cattura con MediaProjection (differita)

Il meccanismo alternativo è catturare il display con `MediaProjection`, elaborare ogni frame su GPU e mostrare il risultato in un overlay a schermo intero. Permette trasformazioni non lineari (LUT 3D), ma introduce rischi che il percorso con matrice evita: possibile auto-cattura dell'overlay (da verificare), latenza (un precedente riporta un ordine di 50–80 ms nel suo caso d'uso), contenuti `FLAG_SECURE` mostrati neri, barre di sistema non coperte, consenso a ogni avvio di sessione, notifica persistente, consumo energetico da misurare.

- Vantaggi: trasformazione arbitraria per pixel.
- Limiti: complessità e rischi elencati sopra.

Esito: **differita**. Si riprende solo se i test di efficacia mostrano che la trasformazione lineare è insufficiente (vedi *Fasi successive (differite)*).

### Modello della percezione e variante a luminanza

Il riferimento teorico per una tricromazia anomala è la simulazione fisiologica di Machado, Oliveira e Fernandes (2009), parametrizzata da una severità tra 0 e 1. Su questo modello si basa la **daltonizzazione classica** (Fidaner et al.): si simula la percezione, si calcola l'errore rispetto all'originale e lo si ridistribuisce sui canali meglio discriminati.

Nelle prove sul dispositivo la daltonizzazione classica ha dato un viraggio verso il viola a intensità alta (rossi che guadagnano blu), accettabile a intensità 0.5. È stata quindi affiancata una **variante a luminanza**, che invece di spostare l'errore sul blu aggiunge luminosità proporzionale alla differenza R−G:

`C = I + β · 1 · [1, −1, 0]`

cioè `R' = (1+β)R − βG`, `G' = βR + (1−β)G`, `B' = βR − βG + B`. Le righe sommano a 1, quindi i **neutri restano invariati**. L'effetto è schiarire e rosare i rossi e scurire i verdi, cambiando la tinta molto meno.

Esempio: con `β = 0.15` il comando è `service call SurfaceFlinger 1015 i32 1 f 1.15 f 0.15 f 0.15 f 0 f -0.15 f 0.85 f -0.15 f 0 f 0 f 0 f 1 f 0 f 0 f 0 f 0 f 1`.

L'ipotesi di lavoro è che la luminanza resti un canale discriminabile per l'utente; gli esiti soggettivi sono coerenti con l'ipotesi ma non la dimostrano. Il valore `β = 0.15` è quello preferito dall'utente nelle prove.

Nota: un filtro riassegna l'informazione cromatica, non ne crea di nuova. L'efficacia su una tricromazia anomala è variabile e non garantita, quindi va misurata su compiti concreti (vedi *Valutazione dell'efficacia*).

### Misura oggettiva della condizione

Per tarare il filtro su dati affidabili serve una misura oggettiva della condizione dell'utente. I test di screening a tavole, come Ishihara, non bastano: indicano la presenza di un deficit rosso-verde ma non ne misurano la gravità. Nello studio di Rodriguez-Carmona e Barbur (2017) la correlazione tra il numero di tavole sbagliate e la gravità della perdita rosso-verde è risultata molto scarsa. Esistono invece strumenti che misurano tipo e gravità:

- **Anomaloscopio** (Nagel, Heidelberg Multi-Colour): standard clinico per tipo e gravità del deficit rosso-verde, basato sul match di Rayleigh.
- **CAD test** (Colour Assessment and Diagnosis, City, University of London): soglie di rilevamento del colore lungo 16 direzioni, con rumore di luminanza che impedisce di usare la luminosità come indizio. Rispetto all'anomaloscopio Nagel, su 223 soggetti, ha specificità 100% e sensibilità 97,5%. Lo screening richiede meno di un minuto, tipo e gravità circa 9 minuti. Si esegue in centri specializzati.
- **Test a soglie su schermo**: il Cambridge Colour Test su tablet calibrato è stato riportato come capace di diagnosticare tipo e gravità; il test Waggoner su iPad è proposto come screener e non come misura di gravità; i test adattivi AIM e FInD su tablet sono in valutazione preliminare.

Un test a soglie eseguito nell'app ha questi pro e contro.

- Vantaggi: misura sullo stesso display su cui agirà il filtro; riproducibile; i parametri del filtro possono essere ricavati direttamente dalle soglie.
- Limiti: la precisione è quella della calibrazione del display. I test digitali devono mostrare gli stessi colori su dispositivi diversi, e i display variano per taratura; uno studio ha dovuto calibrare con cura la luminanza dello schermo di un laptop di consumo. Un telefono non calibrato non fornisce una misura clinica.

La strategia proposta ha tre livelli.

1. **Riferimento clinico.** CAD test o anomaloscopio, una volta, per avere tipo e gravità misurati. TO DO: verificare dove eseguirlo.
2. **Test a soglie nell'app.** Procedura adattiva (staircase) lungo l'asse rosso-verde, con rumore di luminanza e colori generati dai primari misurati del display. La matrice del filtro va azzerata durante il test. Da approfondire: scelta della procedura adattiva e dell'unità di misura (contrasto dei coni oppure unità standard del CAD).
3. **Calibrazione e validazione.** Misurare primari e gamma del display con un colorimetro e confrontare la stima dell'app con il riferimento clinico. Con un solo soggetto è un controllo di coerenza, non una validazione statistica.

Nota: poiché il filtro a luminanza agisce proprio sulla luminanza, la valutazione dell'efficacia deve distinguere i compiti risolvibili con la sola luminosità da quelli che richiedono discriminazione cromatica. Il rumore di luminanza del test a soglie misura la sensibilità cromatica indipendentemente dalla luminosità.

### Sintesi degli approcci

La tabella riassume il confronto; la colonna "Termini incrociati" indica se la trasformazione può mescolare i canali.

| Approccio | Termini incrociati | Personalizzabile | Overhead | Copertura | Esito |
|---|---|---|---|---|---|
| Overlay a blending | No | Solo tinta e opacità | Trascurabile | Solo contenuto app | Scartato |
| Correzione di sistema | Sì (matrice fissa) | No | Nullo | Tutto | Baseline di confronto |
| Matrice via SurfaceFlinger (root) | Sì | Sì (3x3 + offset) | Trascurabile | Tutto (barre e tastiera incluse nelle prove) | **Scelto** |
| MediaProjection + GPU | Sì | Sì (matrice o LUT 3D) | Da misurare | Esclusi contenuti protetti e UI di sistema | Differito |

## Stato attuale

Lo spike A è stato eseguito sul telefono dell'utente con comandi da shell root, senza sviluppare codice. I paragrafi seguenti riportano dispositivo, esiti, ciò che resta da verificare e le fonti consultate.

### Dispositivo di test

Redmi Note 9 Pro, MIUI 14, Android 12, root tramite Magisk.

### Esiti dello spike A

La tabella riporta le prove eseguite e il loro esito, nell'ordine in cui sono state svolte.

| Prova | Esito |
|---|---|
| Scambio R/G (matrice simmetrica) | Funziona; non verifica l'ordine delle colonne |
| `asym` (matrice asimmetrica) | Rosso puro → nero: lettura column-major confermata |
| Daltonizzazione classica, intensità 0.75 | Colori percepiti come strani, virati sul viola |
| Daltonizzazione classica, intensità 0.5 | Accettabile; rosso rosato, verde ok |
| Variante a luminanza, β 0.3 | Rosso e verde distinguibili; rosso tende al rosa, verde più scuro |
| Variante a luminanza, β 0.15 vs 0.5 | Differenza netta; preferito 0.15 |
| Persistenza a schermo spento/acceso | La matrice resta |
| Persistenza dopo toggle Night Light | La matrice resta |
| Copertura di tastiera e barra di stato | Sembrano filtrate (impressione soggettiva) |
| Test di Ishihara (riferito dall'utente) | Circa la metà delle tavole viste; non quantifica la gravità (vedi *Misura oggettiva della condizione*) |
| Esito di `service call` con matrice valida e non valida | In entrambi i casi `Result: Parcel(NULL)` e codice di uscita 0: il comando non segnala se la matrice è stata accettata |

### Cosa resta non verificato

- **Riavvio.** Non provato: è probabile che SurfaceFlinger riparta senza matrice. TO DO verificare al prossimo riavvio.
- **Contenuti protetti** (`FLAG_SECURE`, DRM). Da approfondire.
- **Spazio colore** in cui SurfaceFlinger applica la matrice (lineare o con gamma). Da approfondire.
- **Efficacia su compiti.** Finora solo impressioni soggettive; nessuna misura di errori o tempi.
- **Confronto con la correzione nativa** di Android sul campo. TO DO.
- **Verifica clinica** (anomaloscopio). TO DO.
- **Conferma dell'applicazione.** `service call` risponde `Parcel(NULL)` con codice di uscita 0 anche con una matrice non valida, quindi l'esito del comando non prova che la matrice sia stata applicata. La validità va garantita dal dominio (ultima riga fissa, `β` limitato). Da approfondire.

### Riferimenti tecnici consultati

Fonti usate per le transazioni di SurfaceFlinger e per il percorso differito.

- `DisplayTransformManager` (codici 1014 daltonizzazione, 1015 matrice colore, 1022 saturazione): https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/0461a59e8a73d8e375aad51442c38ce678cfb229
- SurfaceFlinger, matrice column-major con ultima riga (0,0,0,1) e calcolo della matrice effettiva: https://gerrit.omnirom.org/plugins/gitiles/android_frameworks_native/+/28f320b443106f1656f9720224f579136dcf0c61%5E%21
- Esempio d'uso del comando da shell root (Red Moon, issue 150): https://github.com/LibreShift/red-moon/issues/150
- Modulo che usa le transazioni 1015 e 1022 su MIUI 13/14: https://github.com/adivenxnataly/DisplayCalibration
- Precedente `MediaProjection` + overlay (percorso differito): https://github.com/FrontMage/LSFG-Android-Application
- CAD test, accordo con l'anomaloscopio Nagel e soglie: https://openaccess.city.ac.uk/id/eprint/12058/
- CAD test, centro di riferimento e tempi: https://www.city.ac.uk/avot
- Gravità della perdita e correlazione con Ishihara (Rodriguez-Carmona e Barbur, 2017): https://openaccess.city.ac.uk/id/eprint/18074/
- Cambridge Colour Test su tablet calibrato (Chacon et al.): https://optometry.uiw.edu/_docs/chaconetal.pdf
- Test Waggoner su iPad rispetto all'anomaloscopio: https://avehjournal.org/index.php/aveh/article/view/1027
- Test AIM e FInD su tablet (valutazione preliminare): https://www.visionsciences.org/presentation/?id=1352
- Test con movimenti oculari su tablet, con calibrazione della luminanza: https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12565516/

Nota: la licenza di `LSFG-Android-Application` vieta uso commerciale e pubblicazione su store. Va usata solo come riferimento architetturale.

## DRAFT: Ipotesi di funzionamento

Ipotesi di lavoro: un'app minimale con una schermata (interruttore e regolazione dell'intensità β), un componente che calcola la matrice e la invia a SurfaceFlinger via root, persistenza dei due valori e riapplicazione al boot tramite uno script Magisk generato dall'app. Nessuna riapplicazione ad altri eventi finché non si osserva una sovrascrittura reale (le prove non l'hanno mostrata).

DOMANDE:
- In quali situazioni concrete l'utente sbaglia o fa più fatica (indicatori di stato rosso/verde, grafici, mappe, syntax highlighting, UI di app specifiche)? Servono per definire il criterio di successo.
- Il riavvio azzera davvero la matrice?
- È raggiungibile un centro che esegue il CAD test o un anomaloscopio?
- Serve un range di intensità oltre 0.5, o `β` tra 0 e 0.5 è sufficiente?
- Il toggle rapido da tile è necessario subito o basta l'app?

## Principi di sviluppo

L'app va sviluppata con architettura ordinata e scope minimo. La tabella traduce ciascun principio in una scelta concreta di questo progetto, così da poterla verificare in revisione.

| Principio | Applicazione nel progetto |
|---|---|
| **YAGNI** | Solo MVP: matrice a luminanza, interruttore, intensità, riapplicazione al boot. Tutto il resto resta in *Fasi successive (differite)*, con una condizione esplicita di sblocco |
| **KISS** | Modulo unico `app`, iniezione delle dipendenze manuale, niente Hilt né Room; due soli valori persistiti con DataStore Preferences |
| **Single Responsibility** | Una classe per ragione di cambiamento: calcolo della matrice, applicazione al sistema, persistenza, orchestrazione, interfaccia |
| **Dependency Inversion** | `ScreenColorApplier` è un'interfaccia: il dominio non conosce root né SurfaceFlinger. È giustificata dalla testabilità (fake) e dall'instabilità della transazione non pubblica |
| **Open/Closed senza anticipare** | Nessuna interfaccia per il modello di filtro finché esiste una sola variante; si introduce quando arriva la seconda |
| **DRY** | Un solo punto costruisce gli argomenti del comando (`ColorTransform.toSurfaceFlingerArgs()`); `cf.sh` e `genmatrix.py` restano strumenti di sviluppo e fonte dei valori di riferimento per i test |
| **Testabilità** | Dominio in Kotlin puro, senza dipendenze Android, con unit test JUnit; applicazione al sistema testata con una shell finta |
| **Errori espliciti** | `sealed interface ApplyResult`; nessun `catch` silenzioso; lo stato "root non disponibile" è visibile in interfaccia |
| **Minimo privilegio** | Il comando root è costruito solo da float numerici; nessuna stringa dell'utente raggiunge la shell |
| **Immutabilità e flusso unidirezionale** | `ColorTransform` e `FilterSettings` sono `data class` immutabili; stato della UI con state hoisting e `StateFlow` |

Regole operative collegate ai principi:

- Formattare i float con `Locale.ROOT`. Con il locale italiano `0.15` diventerebbe `0,15` e il comando fallirebbe.
- Una responsabilità per branch; commit piccoli (esempio: `feat(domain): aggiungere LuminanceShiftFilter`).
- Definizione di completato: unit test verdi, nessun `TODO` non tracciato in questo documento, nessun codice per requisiti non presenti qui.
- Le semplificazioni intenzionali sono marcate nel codice con un commento `ponytail:` che indica il limite accettato e come superarlo.
- In interfaccia il colore non è mai l'unica informazione: ogni indicazione ha anche un testo.

## Idee di sviluppo

Il flusso proposto procede dal dominio verso l'esterno, in modo che ogni livello sia testabile prima di dipendere dal successivo.

1. Realizzare il dominio puro: `ColorTransform` e `LuminanceShiftFilter`, con unit test.
2. Realizzare l'applicazione al sistema dietro l'interfaccia `ScreenColorApplier`, con implementazione SurfaceFlinger via root.
3. Realizzare persistenza (`SettingsRepository`) e orchestrazione (`FilterController`).
4. Realizzare la schermata unica con interruttore e regolazione dell'intensità.
5. Realizzare la riapplicazione al boot, con la soluzione scelta in base alle DOMANDE.
6. Usare l'app per alcuni giorni, annotando dove il filtro aiuta e dove no.
7. Valutare le fasi differite solo se le condizioni di sblocco si verificano.

## Funzionalità da finalizzare

### MVP: applicazione e controllo del filtro

**Come svilupparlo.** Il dominio calcola la matrice a partire da `β` e la espone come `ColorTransform`, che sa produrre gli argomenti numerici del comando. Un'implementazione di `ScreenColorApplier` li invia a SurfaceFlinger tramite una shell root. Un `FilterController` osserva le impostazioni e chiama l'applier; la schermata comanda il controller tramite un ViewModel.

**Criteri di accettazione.**

- Attivare l'interruttore applica il filtro; disattivarlo ripristina i colori.
- La regolazione dell'intensità aggiorna il filtro in tempo reale, tra 0 e un massimo da fissare (TO DO: partire da 0.5).
- Se il root non è disponibile o il comando fallisce, l'interfaccia lo mostra senza arrestarsi.
- Le impostazioni sopravvivono alla chiusura dell'app.

### Riapplicazione al boot

Il riavvio probabilmente azzera la matrice (vedi *Cosa resta non verificato*), quindi serve riapplicarla all'avvio. L'utente usa Magisk, che esegue all'avvio gli script presenti in `/data/adb/service.d/`. Due alternative:

- **A — `BootReceiver` (`BOOT_COMPLETED`) nell'app.** Vantaggi: tutto nell'app, riusa `FilterController`. Limiti: su MIUI richiede l'autorizzazione di avvio automatico; l'app deve essere stata aperta almeno una volta.
- **B — script Magisk in `/data/adb/service.d/`, generato dall'app.** Vantaggi: indipendente dall'app e dalle restrizioni MIUI al momento del boot; l'app resta l'unica fonte di verità perché riscrive lo script a ogni cambio di impostazioni, quindi `β` non viene duplicato a mano. Limiti: dipende da Magisk; lo script resta sul dispositivo anche se l'app viene disinstallata (la rimozione manuale va documentata).

Decisione: **B**. L'alternativa A è scartata perché aggiunge un componente Android e dipende dall'avvio automatico di MIUI.

**Come svilupparla.** Una classe `BootScriptWriter` scrive lo script (attesa di `sys.boot_completed`, breve ritardo, comando della matrice corrente) in un file temporaneo e lo sposta in `/data/adb/service.d/` con permessi eseguibili; con il filtro disattivato rimuove lo script. Il contenuto è composto solo da testo fisso e valori numerici formattati con `Locale.ROOT`. L'esecuzione dei comandi è condivisa con l'applier tramite `RootShell.execute`, e `FilterController` installa lo script solo se l'applicazione della matrice riesce.

Considerazioni aggiuntive: la scrittura va fatta in modo atomico (file temporaneo e spostamento) per evitare script parziali al boot.

### Fasi successive (differite)

Per rispettare YAGNI, le funzionalità seguenti non fanno parte dell'MVP. Ciascuna ha una condizione esplicita che ne giustifica l'avvio.

| Funzionalità | Condizione di sblocco |
|---|---|
| Quick Settings Tile | Aprire l'app per accendere il filtro risulta scomodo nell'uso quotidiano |
| Riapplicazione su eventi (sblocco, cambio configurazione) | Si osserva una sovrascrittura reale della matrice |
| Daltonizzazione classica come secondo modello | La variante a luminanza è insufficiente sui casi reali |
| Test a soglie nell'app (stile CAD/CCT, display calibrato) | Dopo alcuni giorni d'uso serve stimare i parametri in modo sistematico (vedi *Misura oggettiva della condizione*) |
| Raccolta dei casi reali | Serve un dataset per valutare l'efficacia |
| Riferimento clinico (CAD test o anomaloscopio) | Prima di investire nella taratura fine; serve a validare il test nell'app |
| Calibrazione del display con colorimetro | Si avvia il test a soglie nell'app |
| `MediaProjection` e LUT 3D | La trasformazione lineare si dimostra insufficiente nei test di efficacia |
| Profili per app | Emerge un bisogno concreto |

### Valutazione dell'efficacia

L'efficacia va misurata su compiti, non su impressioni. Il confronto avviene a tre bracci: nessun filtro, correzione nativa, filtro personalizzato.

- Errori e tempi su un insieme fisso di situazioni problematiche (da definire a partire dalla risposta alla DOMANDA sulle situazioni concrete).
- Conteggio delle tavole di Ishihara lette nelle tre condizioni, sullo stesso display e con la stessa luce, usando set o ordini diversi per evitare l'effetto memoria. È un indicatore rapido, non una misura di gravità.
- Naturalezza percepita su contenuti neutri (foto, interfacce senza problemi rosso-verde).

Criterio di successo: il filtro personalizzato riduce errori e tempi rispetto alla correzione nativa senza degradare la naturalezza oltre una soglia (TO DO: definirla).

## Sviluppo

### Rationale

Struttura del codice, con package `com.luigiscialpi.colorblindnessfilter` (repository: https://github.com/luigiscialpi/color-blindness-filter). I file del primo branch sono già presenti; i nomi degli altri sono proposti e vanno confermati branch per branch.

1. **Dominio** (Kotlin puro)
   1. `ColorTransform`: matrice 3x3 immutabile con `toSurfaceFlingerArgs()`, che produce i 16 valori column-major con ultima riga (0,0,0,1) e formattazione `Locale.ROOT` (`domain/ColorTransform.kt`).
   2. `LuminanceShiftFilter`: dato `β`, restituisce il `ColorTransform` con `C = I + β·1·[1, −1, 0]` (`domain/LuminanceShiftFilter.kt`).
2. **Applicazione al sistema**
   1. Interfaccia `ScreenColorApplier` con `apply(transform)` e `reset()`, e `sealed interface ApplyResult` (`system/ScreenColorApplier.kt`).
   2. Interfaccia `RootShell` con `isRootAvailable()` e `run(command)`, e `ShellResult`; la funzione `execute` traduce l'esito in `ApplyResult` ed è condivisa da applier e script di avvio (`system/RootShell.kt`).
   3. `SurfaceFlingerColorApplier`: invia `service call SurfaceFlinger 1015 …` tramite `RootShell`; ripristino con `i32 0`. `Success` indica che il comando è stato eseguito e ha risposto con un `Parcel`, non che la matrice sia stata applicata (`system/SurfaceFlingerColorApplier.kt`).
   4. `LibsuRootShell`: adattatore di `RootShell` sopra `libsu` 6.0.0 (versione indicata dal README del progetto, licenza Apache-2.0, distribuita tramite JitPack) (`system/LibsuRootShell.kt`).
3. **Persistenza**
   1. `FilterSettings` (`enabled: Boolean`, `intensity: Double`): valida l'intervallo dell'intensità e ha come valori iniziali filtro spento e intensità 0.15 (`data/FilterSettings.kt`).
   2. `SettingsRepository` su DataStore Preferences, con `Flow<FilterSettings>` e le funzioni `setEnabled` e `setIntensity`; il legame con il `Context` sta in un file a parte (`data/SettingsRepository.kt`, `data/FilterDataStore.kt`).
4. **Orchestrazione**
   1. `FilterController`: dato un `FilterSettings`, applica la matrice (filtro acceso) o ripristina i colori (filtro spento) tramite l'applier e, solo se l'applicazione riesce, installa o rimuove lo script di avvio. È sincrono e senza coroutine, quindi si testa con un applier finta; l'osservazione delle impostazioni e il cambio di thread restano nel ViewModel (`FilterController.kt`).
5. **Interfaccia**
   1. `FilterUiState`: impostazioni correnti ed esito dell'ultima applicazione (`ui/FilterUiState.kt`).
   2. `FilterViewModel` con `StateFlow`: una coda che conserva solo l'ultima richiesta esegue in ordine le chiamate root bloccanti, per l'anteprima dal vivo durante il trascinamento dello slider; il salvataggio avviene a fine trascinamento (`ui/FilterViewModel.kt`).
   3. `FilterScreen` in Compose: interruttore, slider di intensità a passi di 0.01, tre campioni di riferimento (rosso, verde, grigio) filtrati insieme allo schermo e messaggi di stato in testo (`ui/FilterScreen.kt`).
   4. `MainActivity`: cablaggio manuale delle dipendenze e colori dinamici (`ui/MainActivity.kt`).
6. **Boot**
   1. `BootScriptWriter`: genera e rimuove lo script `service.d` di Magisk a partire dalla matrice corrente (`system/BootScriptWriter.kt`).
7. **Test** (unit test del dominio e del controller)
   1. Con `β = 0` la matrice è l'identità.
   2. Ogni riga della matrice somma a 1 (neutri invariati).
   3. La sequenza prodotta per la matrice asimmetrica `R' = G, G' = G, B' = B` coincide con quella del test `asym` (verifica column-major).
   4. L'ultima riga è (0, 0, 0, 1).
   5. Con `β = 0.15` la sequenza coincide con quella riportata in *Modello della percezione e variante a luminanza*.
   6. Con locale predefinito italiano, la formattazione usa il punto decimale.
   7. `FilterController`: acceso applica la matrice dell'intensità scelta, spento ripristina, l'esito dell'applier viene restituito invariato.
   8. `BootScriptWriter`: lo script contiene il comando atteso con il punto decimale, la scrittura è atomica e il filtro disattivato rimuove lo script. Alcuni test eseguono i comandi con una shell `sh` locale su file temporanei, per verificare quoting e permessi (saltati se `sh` non è disponibile).
   9. `FilterSettings`: valori iniziali, intervallo valido, rifiuto di `NaN` e di valori infiniti.
   10. `SettingsRepository`: valori iniziali, persistenza di acceso e intensità, rifiuto dell'intensità fuori intervallo, ripiego su valori memorizzati non validi.

### Implementazione

La suddivisione in branch segue la convenzione `feature/nome-scopo`. Esempio: `feature/domain-color-transform`.

1. `feature/domain-color-transform`
   1. Creare il progetto Android (Kotlin, senza Compose finché non serve) con un solo modulo `app`.
   2. Implementare `ColorTransform` e `LuminanceShiftFilter`.
   3. Scrivere gli unit test del dominio.
2. `feature/screen-color-applier`
   1. Definire `ScreenColorApplier` e `ApplyResult`.
   2. Implementare `SurfaceFlingerColorApplier` sopra l'interfaccia `RootShell`.
   3. Scrivere i test con una shell finta (comando atteso, reset, errore).
   4. Implementare `LibsuRootShell` e aggiungere `libsu` (repository JitPack limitato al suo gruppo).
3. `feature/settings-controller`
   1. Implementare `FilterSettings` e `FilterController` con i relativi test.
   2. Implementare `SettingsRepository` su DataStore con i relativi test (dipendenza `datastore-preferences` e `android.useAndroidX=true`).
4. `feature/main-screen`
   1. Aggiungere il plugin Compose e le dipendenze (BOM, `activity-compose`, `material3`, `ui`).
   2. Implementare `FilterViewModel` e `FilterScreen`, con `MainActivity` e tema di piattaforma.
   3. Mostrare lo stato "root non disponibile".
5. `feature/magisk-boot-script`
   1. Implementare `BootScriptWriter` (generazione atomica e rimozione dello script).
   2. Estrarre `RootShell.execute` e collegare lo script a `FilterController` e a `MainActivity`.
   3. Scrivere i test con directory temporanea e con una shell locale.
   4. Verificare il comportamento dopo un riavvio reale.

### Checklist

Lo stesso piano come tracker di avanzamento, branch per branch.

`feature/domain-color-transform`
- [x] Progetto Android creato (modulo unico `app`)
- [x] `ColorTransform` con `toSurfaceFlingerArgs()`
- [x] `LuminanceShiftFilter`
- [x] Unit test del dominio (identità, somma righe, column-major, ultima riga, golden β 0.15, locale)

`feature/screen-color-applier`
- [x] `ScreenColorApplier` e `ApplyResult`
- [ ] `SurfaceFlingerColorApplier` via root (adattatore `libsu` da verificare sul dispositivo)
- [x] Test con shell finta

`feature/settings-controller`
- [x] `SettingsRepository` su DataStore
- [x] `FilterController` e relativi test

`feature/main-screen`
- [ ] Plugin Compose e dipendenze Gradle (da verificare con la build)
- [ ] `FilterViewModel` e `FilterScreen` (scritti, da verificare con build e dispositivo)
- [ ] Stato "root non disponibile" (scritto, da verificare sul dispositivo)

`feature/magisk-boot-script`
- [x] `BootScriptWriter` con scrittura atomica e rimozione
- [x] Test con directory temporanea e con shell locale
- [ ] Verifica dopo riavvio reale (da fare sul telefono)

## Elenco file impattati

Elenco dei file del progetto. `ColorTransform`, `LuminanceShiftFilter` e i relativi test sono già presenti; gli altri sono proposti e vanno confermati branch per branch.

- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/domain/ColorTransform.kt`: nuovo, matrice immutabile e argomenti del comando
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/domain/LuminanceShiftFilter.kt`: nuovo, calcolo della matrice a luminanza
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/system/ScreenColorApplier.kt`: nuovo, interfaccia e `ApplyResult`
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/system/SurfaceFlingerColorApplier.kt`: nuovo, invio via shell root
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/system/RootShell.kt`: nuovo, interfaccia della shell root, `ShellResult` ed `execute`
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/system/LibsuRootShell.kt`: nuovo, adattatore `libsu`
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/data/FilterSettings.kt`: nuovo, impostazioni immutabili
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/data/SettingsRepository.kt`: nuovo, DataStore Preferences
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/data/FilterDataStore.kt`: nuovo, legame del DataStore con il `Context`
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/FilterController.kt`: nuovo, orchestrazione
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/ui/FilterUiState.kt`: nuovo, stato della UI
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/ui/FilterViewModel.kt`: nuovo, logica della schermata
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/ui/FilterScreen.kt`: nuovo, schermata Compose
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/ui/MainActivity.kt`: nuovo, host della schermata
- `app/src/main/res/values/themes.xml`: nuovo, tema di piattaforma senza barra del titolo
- `app/src/main/res/values-night/themes.xml`: nuovo, variante scura del tema
- `app/src/main/java/com/luigiscialpi/colorblindnessfilter/system/BootScriptWriter.kt`: nuovo, script `service.d` di Magisk
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/domain/ColorTransformTest.kt`: nuovo, test del dominio
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/domain/LuminanceShiftFilterTest.kt`: nuovo, test del dominio
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/system/SurfaceFlingerColorApplierTest.kt`: nuovo, test con shell finta
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/system/BootScriptWriterTest.kt`: nuovo, test con directory temporanea
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/system/FakeRootShell.kt`: nuovo, shell finta condivisa dai test
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/FilterControllerTest.kt`: nuovo, test del controller
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/data/FilterSettingsTest.kt`: nuovo, test delle impostazioni
- `app/src/test/java/com/luigiscialpi/colorblindnessfilter/data/SettingsRepositoryTest.kt`: nuovo, test della persistenza
- `app/src/main/AndroidManifest.xml`: dichiarazione di applicazione e `MainActivity` avviabile (con l'alternativa B non serve alcun permesso di boot)
- `app/build.gradle.kts`: nuovo, dipendenze (Compose, DataStore, `libsu`)
