package com.offlinecode.editor.run

enum class OutKind { INFO, STDOUT, STDERR }

/** All callbacks are delivered on the main thread. */
interface OutputListener {
    fun onOutput(text: String, kind: OutKind)
    /** exitCode null = stopped/timed out/not applicable. */
    fun onFinished(exitCode: Int?)
}
