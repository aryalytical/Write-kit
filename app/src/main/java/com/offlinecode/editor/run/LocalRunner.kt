package com.offlinecode.editor.run

import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class ExecStep(val cmd: List<String>)
class ExecutionPlan(val steps: List<ExecStep>, val cwd: File, val env: Map<String, String>)

/** Runs real processes from an installed runtime package. Nothing here is simulated. */
class LocalRunner(private val listener: OutputListener) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var current: Process? = null
    @Volatile private var cancelled = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = thread?.isAlive == true

    private fun post(text: String, kind: OutKind) = main.post { listener.onOutput(text, kind) }

    private fun pump(stream: InputStream, kind: OutKind): Thread = Thread {
        try {
            val buf = ByteArray(4096)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                post(String(buf, 0, n, Charsets.UTF_8), kind)
            }
        } catch (e: IOException) {
        }
    }.also { it.start() }

    fun start(plan: ExecutionPlan, timeoutMs: Long = 120_000L) {
        cancelled = false
        thread = Thread {
            var exit: Int? = 0
            try {
                for (step in plan.steps) {
                    if (cancelled) { exit = null; break }
                    post("$ " + step.cmd.joinToString(" ") + "\n", OutKind.INFO)
                    val pb = ProcessBuilder(step.cmd).directory(plan.cwd)
                    pb.environment().putAll(plan.env)
                    val p = pb.start()
                    current = p
                    p.outputStream.close() // no stdin support: programs reading input get EOF
                    val t1 = pump(p.inputStream, OutKind.STDOUT)
                    val t2 = pump(p.errorStream, OutKind.STDERR)
                    if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                        p.destroyForcibly()
                        post("Timed out after ${timeoutMs / 1000}s\n", OutKind.STDERR)
                        exit = null
                        break
                    }
                    t1.join(); t2.join()
                    val code = p.exitValue()
                    if (code != 0) { exit = code; break }
                }
            } catch (e: IOException) {
                post("Failed to start process: ${e.message}\n", OutKind.STDERR)
                if (e.message?.contains("Permission denied") == true || e.message?.contains("error=13") == true) {
                    post("Hint: the file is not executable or the OS blocks exec from app storage. See README → Known limitations.\n", OutKind.INFO)
                }
                exit = -1
            } catch (e: InterruptedException) {
                exit = null
            } finally {
                current = null
            }
            val result = if (cancelled) null else exit
            main.post { listener.onFinished(result) }
        }.also { it.start() }
    }

    fun stop() {
        cancelled = true
        current?.destroyForcibly()
    }
}
