package com.luigiscialpi.colorblindnessfilter.system

import com.luigiscialpi.colorblindnessfilter.domain.ColorTransform

/**
 * Applica la matrice colore con la transazione 1015 di SurfaceFlinger, tramite shell root.
 *
 * La transazione non è un'API pubblica: tenerla isolata qui permette di sostituirla senza
 * toccare il resto dell'app. Il comando è composto solo da testo fisso e numeri prodotti
 * da [ColorTransform], mai da stringhe esterne.
 *
 * [ApplyResult.Success] significa che il comando è stato eseguito e `service call` ha
 * risposto con un `Parcel`. Non conferma che la matrice sia stata applicata: sul dispositivo
 * di prova il comando risponde `Parcel(NULL)` con codice di uscita 0 anche per una matrice
 * non valida. La validità della matrice è garantita da [ColorTransform].
 */
class SurfaceFlingerColorApplier(private val shell: RootShell) : ScreenColorApplier {

    override fun apply(transform: ColorTransform): ApplyResult =
        shell.execute("$TRANSACTION i32 1 ${transform.toSurfaceFlingerArgs()}", REPLY_MARKER)

    override fun reset(): ApplyResult = shell.execute("$TRANSACTION i32 0", REPLY_MARKER)

    private companion object {
        const val TRANSACTION = "service call SurfaceFlinger 1015"
        const val REPLY_MARKER = "Parcel("
    }
}
