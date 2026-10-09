package com.luigiscialpi.colorblindnessfilter.system

/** Shell finta per i test: registra i comandi ricevuti e risponde sempre con [result]. */
class FakeRootShell(
    private val rootAvailable: Boolean = true,
    private val result: ShellResult,
) : RootShell {
    val commands = mutableListOf<String>()

    override fun isRootAvailable(): Boolean = rootAvailable

    override fun run(command: String): ShellResult {
        commands.add(command)
        return result
    }
}
