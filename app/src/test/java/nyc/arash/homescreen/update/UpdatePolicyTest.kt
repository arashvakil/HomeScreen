package nyc.arash.homescreen.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdatePolicyTest {
    @Test fun onlyHttpsWithoutEmbeddedCredentialsIsAccepted() {
        assertTrue(UpdatePolicy.isSecureUrl("https://github.com/example/app/releases/latest/download/update.json"))
        listOf("http://example.com/update.json", "file:///tmp/update.json", "", "not a url", "https://user:pass@example.com/app.apk")
            .forEach { assertFalse(it, UpdatePolicy.isSecureUrl(it)) }
    }

    @Test fun manifestsRequireVersionSecureApkAndCompleteDigest() {
        val valid = UpdateManifest(3, "1.0", "https://example.com/app.apk", "a".repeat(64), "")
        assertTrue(UpdatePolicy.isValidManifest(valid))
        assertTrue(UpdatePolicy.isValidManifest(valid.copy(sha256 = "A".repeat(64))))
        assertFalse(UpdatePolicy.isValidManifest(valid.copy(versionCode = 0)))
        assertFalse(UpdatePolicy.isValidManifest(valid.copy(apkUrl = "http://example.com/app.apk")))
        assertFalse(UpdatePolicy.isValidManifest(valid.copy(sha256 = "a".repeat(63))))
        assertFalse(UpdatePolicy.isValidManifest(valid.copy(sha256 = "z".repeat(64))))
    }

    @Test fun apkMustMatchPackageExpectedNewVersionAndExactNonemptySignerSet() {
        fun accepts(pkg: String = "launcher", version: Long = 3L, keys: Set<String> = setOf("key"), expected: Int? = 3) =
            UpdatePolicy.acceptsApk("launcher", 2L, setOf("key"), pkg, version, keys, expected)
        assertTrue(accepts())
        assertFalse(accepts(pkg = "another.app"))
        assertFalse(accepts(version = 2L))
        assertFalse(accepts(version = 1L))
        assertFalse(accepts(version = 4L))
        assertFalse(accepts(keys = setOf("other-key")))
        assertFalse(accepts(keys = setOf("key", "other-key")))
        assertFalse(accepts(keys = emptySet()))
        assertFalse(UpdatePolicy.acceptsApk("launcher", 2L, emptySet(), "launcher", 3L, emptySet(), 3))
    }

    @Test fun sha256UsesActualFileContents() {
        val file = File.createTempFile("launcher-update-test", ".apk")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdatePolicy.sha256(file))
            file.appendText("changed")
            assertFalse(UpdatePolicy.sha256(file) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        } finally { file.delete() }
    }
}
