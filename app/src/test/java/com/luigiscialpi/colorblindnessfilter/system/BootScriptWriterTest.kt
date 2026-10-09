package com.luigiscialpi.colorblindnessfilter.system

import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BootScriptWriterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val ok = ShellResult(isSuccess = true, output = emptyList())
    private val transform = LuminanceShiftFilter.transform(0.15)

    /** Esegue davvero i comandi con `sh` locale, per verificare il quoting e i permessi. */
    private val localShell = object : RootShell {
        override fun isRootAvailable(): Boolean = true

        override fun run(command: String): ShellResult {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readLines()
            return ShellResult(isSuccess = process.waitFor() == 0, output = output)
        }
    }

    private fun requireLocalSh() = assumeTrue(File("/bin/sh").canExecute())

    @Test
    fun installInviaUnSoloComandoConScriptEPermessi() {
        val shell = FakeRootShell(result = ok)

        val outcome = BootScriptWriter(shell, directory = "/dir").install(transform)

        assertEquals(ApplyResult.Success, outcome)
        val command = shell.commands.single()
        assertTrue(command.startsWith("mkdir -p '/dir' && printf '%s\\n' "))
        assertTrue(command.contains("service call SurfaceFlinger 1015 i32 1 ${transform.toSurfaceFlingerArgs()}"))
        assertTrue(
            command.endsWith(
                "mv '/dir/.color-blindness-filter.tmp' '/dir/color-blindness-filter.sh' && " +
                    "chmod 755 '/dir/color-blindness-filter.sh'",
            ),
        )
    }

    @Test
    fun removeEliminaLoScript() {
        val shell = FakeRootShell(result = ok)

        val outcome = BootScriptWriter(shell, directory = "/dir").remove()

        assertEquals(ApplyResult.Success, outcome)
        assertEquals(listOf("rm -f '/dir/color-blindness-filter.sh'"), shell.commands)
    }

    @Test
    fun senzaRootNonEseguePiuNessunComando() {
        val shell = FakeRootShell(rootAvailable = false, result = ok)
        val writer = BootScriptWriter(shell, directory = "/dir")

        assertEquals(ApplyResult.RootUnavailable, writer.install(transform))
        assertEquals(ApplyResult.RootUnavailable, writer.remove())
        assertTrue(shell.commands.isEmpty())
    }

    @Test
    fun unComandoFallitoRiportaLOutputDellaShell() {
        val shell = FakeRootShell(result = ShellResult(false, listOf("permesso negato")))

        val outcome = BootScriptWriter(shell, directory = "/dir").install(transform)

        assertEquals(ApplyResult.Failed("permesso negato"), outcome)
    }

    @Test
    fun loScriptAspettaIlBootEPoiApplicaLaMatrice() {
        val lines = BootScriptWriter(FakeRootShell(result = ok)).scriptLines(transform)

        assertEquals("#!/system/bin/sh", lines.first())
        assertTrue(lines.any { it.contains("sys.boot_completed") })
        assertEquals(
            "service call SurfaceFlinger 1015 i32 1 ${transform.toSurfaceFlingerArgs()}",
            lines.last(),
        )
        assertTrue(lines.none { it.contains('\'') })
    }

    @Test
    fun conUnaShellVeraCreaLoScriptEseguibileESenzaFileTemporanei() {
        requireLocalSh()
        val directory = temporaryFolder.newFolder("service.d")
        val writer = BootScriptWriter(localShell, directory = directory.path)

        val outcome = writer.install(transform)

        assertEquals(ApplyResult.Success, outcome)
        val script = File(directory, BootScriptWriter.SCRIPT_NAME)
        assertEquals(writer.scriptLines(transform), script.readLines())
        assertTrue(script.canExecute())
        assertEquals(listOf(BootScriptWriter.SCRIPT_NAME), directory.list()!!.toList())
    }

    @Test
    fun conUnaShellVeraUnaSecondaInstallazioneSostituisceLoScript() {
        requireLocalSh()
        val directory = temporaryFolder.newFolder("service.d")
        val writer = BootScriptWriter(localShell, directory = directory.path)
        val stronger = LuminanceShiftFilter.transform(0.3)

        writer.install(transform)
        writer.install(stronger)

        val script = File(directory, BootScriptWriter.SCRIPT_NAME)
        assertEquals(writer.scriptLines(stronger), script.readLines())
        assertEquals(listOf(BootScriptWriter.SCRIPT_NAME), directory.list()!!.toList())
    }

    @Test
    fun conUnaShellVeraRemoveEliminaLoScript() {
        requireLocalSh()
        val directory = temporaryFolder.newFolder("service.d")
        val writer = BootScriptWriter(localShell, directory = directory.path)
        writer.install(transform)

        val outcome = writer.remove()

        assertEquals(ApplyResult.Success, outcome)
        assertFalse(File(directory, BootScriptWriter.SCRIPT_NAME).exists())
    }

    @Test
    fun conUnaShellVeraLoScriptContieneIlTestoEsattoSenzaEspansioni() {
        requireLocalSh()
        val directory = temporaryFolder.newFolder("service.d")
        val writer = BootScriptWriter(localShell, directory = directory.path)

        writer.install(transform)

        val content = File(directory, BootScriptWriter.SCRIPT_NAME).readText()
        assertTrue(content.contains("until [ \"\$(getprop sys.boot_completed)\" = 1 ]; do sleep 2; done"))
    }
}
