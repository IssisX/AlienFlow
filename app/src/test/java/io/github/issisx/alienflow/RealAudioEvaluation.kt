package io.github.issisx.alienflow

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * Paid, opt-in measurement with the owner's recordings. Excluded from every build unless
 * Gradle receives -PalienflowEval; see docs/EVALUATION.md. The report contains dictated text
 * and stays in app/build; it is never a CI artifact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RealAudioEvaluation {
    private fun env(name: String) = System.getenv(name)?.trim().orEmpty()
    private fun list(name: String, default: String) = env(name).ifEmpty { default }.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    @Test fun measureRecordings() {
        val key = env("OPENAI_API_KEY")
        val directory = File(env("ALIENFLOW_EVAL_DIR"))
        require(key.isNotEmpty()) { "Set OPENAI_API_KEY in the environment; it is never read from files or logged" }
        require(directory.isDirectory) { "Set ALIENFLOW_EVAL_DIR to a folder of NAME.wav + NAME.txt pairs" }
        val recordings = directory.listFiles { f -> f.extension == "wav" }!!.sortedBy { it.name }
            .filter { File(it.parentFile, it.nameWithoutExtension + ".txt").isFile }
        require(recordings.isNotEmpty()) { "No NAME.wav with a matching NAME.txt reference in $directory" }
        val variants = list("ALIENFLOW_EVAL_DELAY", "medium").flatMap { delay ->
            list("ALIENFLOW_EVAL_SILENCE_MS", "850").map { silence ->
                RealAudioRun.Variant(delay, silence.toLong(), env("ALIENFLOW_EVAL_PROMPT"),
                    env("ALIENFLOW_EVAL_MODE").ifEmpty { "B" }, env("ALIENFLOW_EVAL_LANGUAGE").ifEmpty { "en" })
            }
        }
        val api = OpenAiClient()
        val runs = JSONArray()
        for (variant in variants) for (wav in recordings) {
            fun sibling(suffix: String) = File(wav.parentFile, wav.nameWithoutExtension + suffix).takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()
            val run = RealAudioRun(api, key, variant)
            val result = run.run(RealAudioRun.readWav(wav), WritingContext(sibling(".before.txt"), sibling(".after.txt")))
            runs.put(run.report(wav.nameWithoutExtension, sibling(".txt").trim(), result))
        }
        val output = File("build/alienflow-eval").apply { mkdirs() }
        val report = File(output, "report-${Instant.now().toString().replace(':', '-')}.json")
        report.writeText(JSONObject().put("notice", "Contains dictated text. Keep private; do not commit or upload.")
            .put("source_revision", BuildConfig.SOURCE_REVISION).put("runs", runs).toString(2), Charsets.UTF_8)
        println("AlienFlow evaluation report: ${report.absolutePath}")
        val failed = (0 until runs.length()).map { runs.getJSONObject(it) }.filter { it.getJSONArray("problems").length() > 0 }
        assertTrue("Runs with protocol/provider problems (see report): ${failed.map { it.getString("recording") }}", failed.isEmpty())
    }
}
