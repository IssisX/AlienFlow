package io.github.issisx.alienflow

import android.app.AlertDialog
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

/** Native activities and production persistence/navigation. All transcript content is a test fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsNavigationTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun tagged(activity: MainActivity, tag: String) = descendants(activity.window.decorView).single { it.tag == tag }
    private fun button(activity: MainActivity, label: String) = descendants(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == label }
    private fun row(activity: MainActivity, label: String) = descendants(activity.window.decorView).single { it.contentDescription?.toString()?.startsWith("$label.") == true }
    private fun texts(activity: MainActivity) = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
    private fun app(activity: MainActivity) = activity.application as AlienApp
    private fun render(activity: MainActivity, name: String, width: Int = 822, height: Int = 1782) {
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/reports/ui-proof").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun historyDoesNotLeakIntoHomeOrSettingsAndCategoriesHaveIndependentPages() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            app(activity).store.saveResult("Fixture: a long transcript", "Fixture: a long transcript", "complete")
            assertFalse(texts(activity).any { it.contains("Fixture: a long transcript") })
            tagged(activity, "nav-SETTINGS").performClick()
            assertFalse(texts(activity).any { it.contains("Fixture: a long transcript") })
            assertFalse(descendants(activity.window.decorView).any { it is Switch || it is SeekBar })
            render(activity, "settings-0.1.13.png")
            row(activity, "Floating alien").performClick()
            assertTrue(tagged(activity, "size") is SeekBar)
            assertFalse(texts(activity).contains("Writing style"))
            render(activity, "bubble-settings-0.1.13.png")
            tagged(activity, "back").performClick()
            assertTrue(row(activity, "Language & vocabulary").isShown)
            row(activity, "Dictation & writing").performClick()
            render(activity, "dictation-settings-0.1.13.png")
            assertFalse(descendants(activity.window.decorView).any { it.tag == "size" })
        }
    }
    @Test fun preferencesUseTheExistingKeysAndSurviveActivityRecreation() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            var activity = controller.get()
            tagged(activity, "nav-SETTINGS").performClick(); row(activity, "Dictation & writing").performClick()
            (tagged(activity, "live_display") as Switch).performClick()
            val group = tagged(activity, "correction") as RadioGroup
            (0 until group.childCount).map { group.getChildAt(it) }.single { it.tag == "C" }.performClick()
            assertFalse(app(activity).store.prefs.getBoolean("live_display", true))
            assertEquals("C", app(activity).store.mode())
            controller.recreate(); activity = controller.get()
            assertFalse((tagged(activity, "live_display") as Switch).isChecked)
            assertEquals("C", (tagged(activity, "correction") as RadioGroup).let { it.findViewById<RadioButton>(it.checkedRadioButtonId).tag })
            tagged(activity, "back").performClick()
            row(activity, "Language & vocabulary").performClick()
            (tagged(activity, "vocabulary") as EditText).setText("Fixture terms: Siobhan, AlienFlow")
            tagged(activity, "back").performClick(); row(activity, "Language & vocabulary").performClick()
            assertEquals("Fixture terms: Siobhan, AlienFlow", (tagged(activity, "vocabulary") as EditText).text.toString())
            tagged(activity, "spoken-language").performClick()
            ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 2, 2)
            assertEquals("es", app(activity).store.language())
            assertEquals("Spanish", (tagged(activity, "spoken-language") as Button).text.toString())
        }
    }
    @Test fun historyVirtualizesPreviewsSearchesFullTextAndDeletesOnlyAfterConfirmation() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get(); val store = app(activity).store
            repeat(120) { index -> store.saveResult("Fixture original $index", "Fixture transcript $index " + "long words ".repeat(60), "complete") }
            tagged(activity, "nav-HISTORY").performClick()
            render(activity, "history-0.1.13.png")
            val list = tagged(activity, "transcript-list") as ListView
            assertEquals(120, list.adapter.count)
            assertTrue("Only visible rows should be inflated", list.childCount < 15)
            assertTrue(descendants(list).filterIsInstance<TextView>().filter { it.text.startsWith("Fixture transcript") }.all { it.maxLines == 2 })
            (tagged(activity, "history-search") as EditText).setText("original 42")
            assertEquals(1, list.adapter.count)
            list.performItemClick(null, 0, 0)
            assertTrue((tagged(activity, "transcript-text") as TextView).text.startsWith("Fixture transcript 42"))
            button(activity, "Copy transcript").performClick()
            assertEquals((tagged(activity, "transcript-text") as TextView).text.toString(), activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
            button(activity, "Delete transcript").performClick()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertEquals(120, store.history().size)
            button(activity, "Delete transcript").performClick()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(119, store.history().size)
            assertEquals(0, (tagged(activity, "transcript-list") as ListView).adapter.count)
        }
    }
    @Test fun draftAndNavigationReturnToTheirActualEntryPointWithoutSavingAnUnsavedKey() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            var activity = controller.get()
            (tagged(activity, "practice") as EditText).setText("Fixture draft")
            button(activity, "Finish setup").performClick()
            val key = tagged(activity, "api-key") as EditText
            assertFalse(key.isSaveEnabled)
            key.setText("fixture-unsaved-secret")
            controller.recreate(); activity = controller.get()
            assertEquals("", (tagged(activity, "api-key") as EditText).text.toString())
            assertFalse(app(activity).store.prefs.contains("api_key"))
            tagged(activity, "back").performClick()
            assertEquals("Fixture draft", (tagged(activity, "practice") as EditText).text.toString())
        }
    }
    @Test fun diagnosticToggleIsUnderHelpAndPrivacyUsesExistingRetention() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            tagged(activity, "nav-SETTINGS").performClick(); row(activity, "Help & about").performClick()
            (tagged(activity, "diagnostic_bubble_errors") as Switch).performClick()
            assertFalse(app(activity).store.prefs.getBoolean("diagnostic_bubble_errors", true))
            tagged(activity, "back").performClick(); row(activity, "Privacy & saving").performClick()
            val group = tagged(activity, "retention") as RadioGroup
            (0 until group.childCount).map { group.getChildAt(it) }.single { it.tag == "30" }.performClick()
            assertEquals(30, app(activity).store.prefs.getInt("retention", 7))
            render(activity, "privacy-settings-0.1.13.png")
        }
    }
    @Test fun floatingMenuSettingsIntentOpensSettingsAndDoesNotResetExistingPreferences() {
        val intent = android.content.Intent().setAction(MainActivity.OPEN_SETTINGS)
        Robolectric.buildActivity(MainActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            assertTrue(row(activity, "Floating alien").isShown)
            tagged(activity, "nav-HOME").performClick()
            (tagged(activity, "practice") as EditText).setText("Fixture draft from home")
            controller.newIntent(intent)
            assertTrue(row(activity, "Language & vocabulary").isShown)
            tagged(activity, "nav-HOME").performClick()
            assertEquals("Fixture draft from home", (tagged(activity, "practice") as EditText).text.toString())
        }
    }
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun settingsRemainReachableOnSmallPhoneWithLargerText() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            // Apply font scaling through Android's configuration, then rebuild the native page.
            val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.5f }
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            tagged(activity, "nav-SETTINGS").performClick()
            render(activity, "settings-small-large-text-0.1.13.png", 720, 1600)
            val nav = tagged(activity, "nav-HOME")
            assertTrue(nav.height >= (48 * activity.resources.displayMetrics.density).toInt())
            assertTrue(nav.bottom <= 1600)
            row(activity, "Language & vocabulary").performClick()
            render(activity, "language-small-large-text-0.1.13.png", 720, 1600)
            assertTrue(tagged(activity, "vocabulary").isShown)
        }
    }
    @Test @Config(qualifiers = "w840dp-h891dp-xhdpi")
    fun unfoldedSettingsUseAReadableCenteredWidth() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            tagged(activity, "nav-SETTINGS").performClick()
            render(activity, "settings-unfolded-0.1.13.png", 1680, 1782)
            val category = row(activity, "Floating alien")
            val point = IntArray(2); category.getLocationOnScreen(point)
            assertTrue(category.width <= 1440)
            assertTrue("Reading column should be centered", point[0] > 100)
        }
    }
}
