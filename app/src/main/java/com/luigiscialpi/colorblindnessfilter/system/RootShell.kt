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
