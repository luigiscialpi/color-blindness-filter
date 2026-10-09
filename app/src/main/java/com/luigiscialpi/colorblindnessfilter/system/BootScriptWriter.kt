package com.luigiscialpi.colorblindnessfilter.system

import com.luigiscialpi.colorblindnessfilter.domain.ColorTransform

/**
 * Mantiene lo script Magisk che riapplica la matrice a ogni avvio.
 *
 * Magisk esegue all'avvio gli script eseguibili presenti in `/data/adb/service.d/`. L'app
 * riscrive lo script a ogni cambio di impostazioni, così resta l'unica fonte di verità.
 * Il comando è composto solo da testo fisso e numeri prodotti da [ColorTransform]; i singoli
 * valori sono tra apici singoli, quindi nessuna riga dello script può contenerne uno.
 */
class BootScriptWriter(
    private val shell: RootShell,
    private val directory: String = DEFAULT_DIRECTORY,
) {
    private val scriptPath = "$directory/$SCRIPT_NAME"
    private val temporaryPath = "$directory/$TEMPORARY_NAME"

    /** Scrive lo script che applica [transform] all'avvio, sostituendo quello esistente. */
    fun install(transform: ColorTransform): ApplyResult = shell.execute(installCommand(transform))

    /** Elimina lo script: all'avvio successivo i colori restano quelli originali. */
    fun remove(): ApplyResult = shell.execute("rm -f '$scriptPath'")

    internal fun scriptLines(transform: ColorTransform): List<String> = listOf(
        "#!/system/bin/sh",
        "# Generato da Color Blindness Filter: non modificare a mano.",
        "until [ \"\$(getprop sys.boot_completed)\" = 1 ]; do sleep 2; done",
        "sleep 5",
        "service call SurfaceFlinger 1015 i32 1 ${transform.toSurfaceFlingerArgs()}",
    )

    // ponytail: scrittura su file temporaneo e poi `mv` (stessa cartella, quindi atomico); i
    // permessi si impostano dopo lo spostamento. Se il telefono si riavvia in quella finestra
    // di millisecondi lo script viene saltato una volta sola.
    private fun installCommand(transform: ColorTransform): String {
        val lines = scriptLines(transform)
        check(lines.none { it.contains('\'') }) { "Lo script non può contenere apici singoli" }
        val printfArgs = lines.joinToString(" ") { "'$it'" }
        return "mkdir -p '$directory' && " +
            "printf '%s\\n' $printfArgs > '$temporaryPath' && " +
            "mv '$temporaryPath' '$scriptPath' && " +
            "chmod 755 '$scriptPath'"
    }

    companion object {
        const val DEFAULT_DIRECTORY = "/data/adb/service.d"
        const val SCRIPT_NAME = "color-blindness-filter.sh"
        private const val TEMPORARY_NAME = ".color-blindness-filter.tmp"
    }
}
