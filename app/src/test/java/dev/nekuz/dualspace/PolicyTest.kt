package dev.nekuz.dualspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {

    @Test
    fun acceptsApksInInstalledRoots() {
        assertTrue(isInstalledApkPath("/data/app/~~abc==/com.example-xyz==/base.apk"))
        assertTrue(isInstalledApkPath("/mnt/expand/1234/app/com.example/split_config.apk"))
    }

    @Test
    fun rejectsPathsOutsideInstalledRoots() {
        assertFalse(isInstalledApkPath("/sdcard/Download/evil.apk"))
        assertFalse(isInstalledApkPath("/data/data/com.evil/base.apk"))
        assertFalse(isInstalledApkPath("/data/appfake/base.apk"))
    }

    @Test
    fun rejectsFilesThatAreNotApks() {
        assertFalse(isInstalledApkPath("/data/app/com.example/lib/libfoo.so"))
        assertFalse(isInstalledApkPath("/data/app/com.example/base.apk.bak"))
    }

    @Test
    fun iconSizeFollowsDensity() {
        assertEquals(56, iconSizePx(56, 1f))
        assertEquals(168, iconSizePx(56, 3f))
    }

    @Test
    fun iconSizeIsClamped() {
        assertEquals(256, iconSizePx(56, 100f))
        assertEquals(1, iconSizePx(56, 0f))
    }

    @Test
    fun labelIsReturnedWhenPresent() {
        assertEquals("Mail", safeLabel("com.example") { "Mail" })
    }

    @Test
    fun labelFallsBackWhenBlankOrNull() {
        assertEquals("com.example", safeLabel("com.example") { "   " })
        assertEquals("com.example", safeLabel("com.example") { null })
    }

    @Test
    fun labelFallsBackOnExceptionAndError() {
        assertEquals("com.example", safeLabel("com.example") { throw IllegalStateException() })
        assertEquals("com.example", safeLabel("com.example") { throw OutOfMemoryError() })
    }

    @Test
    fun gateIsIdleWhenNew() {
        assertTrue(InstallGate().isIdle)
    }

    @Test
    fun gateStaysOpenUntilLastHolderCloses() {
        val gate = InstallGate()
        gate.open()
        gate.open()
        assertFalse(gate.close())
        assertFalse(gate.isIdle)
        assertTrue(gate.close())
        assertTrue(gate.isIdle)
    }

    @Test
    fun extraCloseDoesNotUnderflow() {
        val gate = InstallGate()
        assertTrue(gate.close())
        gate.open()
        assertFalse(gate.isIdle)
    }
}
