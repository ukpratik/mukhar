package io.github.ukpratik.mukhar.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code Mukhar runs from launch to an interactive Today and on into the Talk paths (Today's talk, Speak
 * Studio) into the Baseline Profile shipped in the release APK (NFR-002, D-036).
 *
 * Run with a device or an emulator (API 33+, a "Google APIs" image, not rooted is fine) connected:
 * `ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:generateReleaseBaselineProfile`
 * The profile lands in `app/src/release/generated/baselineProfiles/` (baseline-prof.txt and startup-prof.txt);
 * commit both. Re-run after big UI changes.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        finishOnboardingIfShown()
        todayToTalk()
    }
}
