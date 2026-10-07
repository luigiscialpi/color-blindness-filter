package com.luigiscialpi.colorblindnessfilter.system

import com.topjohnwu.superuser.Shell

/**
 * Adattatore di [RootShell] sopra libsu.
 *
 * Le chiamate sono bloccanti: [Shell.getShell] costruisce la shell principale alla prima
 * richiesta (e può mostrare la richiesta di root), quindi vanno fatte fuori dal thread
 * principale. Se il root non è concesso, libsu ripiega su una shell senza privilegi e
 * [isRootAvailable] restituisce false.
 */
class LibsuRootShell : RootShell {

    override fun isRootAvailable(): Boolean = Shell.getShell().isRoot

    override fun run(command: String): ShellResult {
        val result = Shell.cmd(command).exec()
        return ShellResult(isSuccess = result.isSuccess, output = result.out)
    }
}
