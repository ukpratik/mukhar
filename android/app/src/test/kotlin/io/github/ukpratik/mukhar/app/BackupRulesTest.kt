package io.github.ukpratik.mukhar.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * NFR-014, LLD §2.1: Auto Backup includes Room and the settings DataStore only, so recordings, the session token and
 * the debug clock stay on the device (excluded by omission).
 */
class BackupRulesTest {
    private fun includes(file: String, section: String? = null): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val root = section?.let { doc.getElementsByTagName(it).item(0) as Element } ?: doc.documentElement
        val nodes = root.getElementsByTagName("include")
        return (0 until nodes.length).map { (nodes.item(it) as Element).let { e -> e.getAttribute("domain") + ":" + e.getAttribute("path") } }
    }

    private val expected = listOf(
        "database:mukhar.db",
        "database:mukhar.db-wal",
        "database:mukhar.db-shm",
        "file:datastore/settings.preferences_pb",
    )

    @Test
    fun cloudBackupAndDeviceTransferIncludeOnlyRoomAndSettings() {
        assertThat(includes("data_extraction_rules.xml", "cloud-backup")).containsExactlyElementsIn(expected)
        assertThat(includes("data_extraction_rules.xml", "device-transfer")).containsExactlyElementsIn(expected)
    }

    @Test
    fun legacyFullBackupMatches() {
        assertThat(includes("full_backup_content.xml")).containsExactlyElementsIn(expected)
    }

    @Test
    fun manifestPointsAtBothFiles() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertThat(manifest).contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        assertThat(manifest).contains("android:fullBackupContent=\"@xml/full_backup_content\"")
    }

    /** ADR-008: own API keys (byok.pb) and their Tink keyset never leave the phone, in any backup or transfer. */
    @Test
    fun ownKeysAndTheirKeysetAreNeverBackedUp() {
        val all = includes("data_extraction_rules.xml", "cloud-backup") + includes("data_extraction_rules.xml", "device-transfer") +
            includes("full_backup_content.xml")
        assertThat(all.none { "byok" in it }).isTrue()
        assertThat(all.none { it.startsWith("sharedpref") || it == "file:datastore" || it == "file:." || it == "root:." }).isTrue()
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertThat(rules).contains("byok.pb")
        assertThat(rules).contains("mukhar_byok_keyset")
    }
}
