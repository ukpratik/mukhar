package io.github.ukpratik.mukhar.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val PACKAGE = "io.github.ukpratik.mukhar"
internal const val TIMEOUT_MS = 5_000L

/**
 * A fresh install opens onboarding (S01–S06). The quickest path that needs no speaking: an adult birth year,
 * the default languages and goals, "Not now" for the mic, "Continue without mic", "No reminder for now".
 * Does nothing when Today is already showing. Text matches the English strings in app/src/main/res/values.
 */
internal fun MacrobenchmarkScope.finishOnboardingIfShown() {
    if (device.wait(Until.hasObject(By.text("Today")), 2_000L)) return
    device.findObject(By.clazz("android.widget.EditText"))?.let {
        it.click()
        it.text = "1995"
    }
    tap("Continue")
    tap("Continue")
    tap("Not now")
    tap("Continue without mic")
    tap("No reminder for now")
    tap("Go to Today")
    device.wait(Until.hasObject(By.text("Today")), TIMEOUT_MS)
}

/** Today → Today's talk card (S09 recorder, permission dialog dismissed) → back → Practice (S08 Speak Studio). */
internal fun MacrobenchmarkScope.todayToTalk() {
    device.wait(Until.hasObject(By.descContains("Start talk")), TIMEOUT_MS)
    device.findObject(By.descContains("Start talk"))?.let {
        it.click()
        device.waitForIdle()
        // The mic permission dialog (first time only) or the recorder; either way go back to Today.
        device.findObject(By.res("com.android.permissioncontroller:id/permission_deny_button"))?.click()
        device.pressBack()
        device.wait(Until.hasObject(By.text("Today")), TIMEOUT_MS)
    }
    tap("Practice")
    device.wait(Until.hasObject(By.text("Speak Studio")), TIMEOUT_MS)
    device.findObject(By.scrollable(true))?.fling(androidx.test.uiautomator.Direction.DOWN)
    tap("Today")
}

private fun MacrobenchmarkScope.tap(text: String) {
    device.wait(Until.hasObject(By.text(text)), TIMEOUT_MS)
    device.findObject(By.text(text))?.click()
    device.waitForIdle()
}
