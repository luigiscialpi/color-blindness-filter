package com.luigiscialpi.colorblindnessfilter.system

/** Esito di un comando eseguito con privilegi di root. */
data class ShellResult(val isSuccess: Boolean, val output: List<String>)

/**
 * Accesso a una shell root.
 *
 * Le chiamate sono bloccanti (la prima può mostrare la richiesta di root): vanno fatte
 * fuori dal thread principale.
 */
interface RootShell {
    fun isRootAvailable(): Boolean

    fun run(command: String): ShellResult
}

/**
 * Esegue [command] e ne traduce l'esito in [ApplyResult].
 *
 * Con [replyMarker] il successo richiede anche che il testo compaia nell'output (per
 * `service call`, che risponde con un `Parcel`). Senza marcatore basta il codice di uscita 0.
 */
fun RootShell.execute(command: String, replyMarker: String? = null): ApplyResult {
    if (!isRootAvailable()) return ApplyResult.RootUnavailable
    val result = run(command)
    val output = result.output.joinToString("\n")
    if (result.isSuccess && (replyMarker == null || output.contains(replyMarker))) {
        return ApplyResult.Success
    }
    return ApplyResult.Failed(output.ifBlank { "comando fallito senza output" })
}
