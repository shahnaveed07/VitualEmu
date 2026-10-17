package com.dsc.virtualemu

import android.view.KeyEvent
import com.dsc.virtualemu.domain.mapping.KeyNameMapper
import com.dsc.virtualemu.domain.mapping.KeymapCfgParser
import com.dsc.virtualemu.domain.mapping.KeymapExportManager
import com.dsc.virtualemu.domain.model.MappingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeymapCfgImportExportTest {

    @Test
    fun testParseSampleFreeFireCfg_correctlyParsesCustomAndMainSchemesAndControls() {
        val sampleFile = File("C:/Users/snave/NaveedMushtaq/PersonalProjects/FreeFireKeyMap/com.dts.freefireth.cfg")
        assertTrue("Sample config file should exist on system", sampleFile.exists())

        val content = sampleFile.readText()
        val parsed = KeymapCfgParser.parseCfgJson(content, "game-freefire-123")

        assertNotNull(parsed)
        assertEquals("17", parsed.parserVersion)
        assertEquals(2, parsed.profiles.size)

        // Scheme 1: CUSTOM
        val customScheme = parsed.profiles[0]
        assertEquals("CUSTOM", customScheme.name)
        assertTrue("CUSTOM scheme should be selected as default", customScheme.isFavorite)
        assertEquals(38, customScheme.mappings.size)

        // Scheme 2: MAIN
        val mainScheme = parsed.profiles[1]
        assertEquals("MAIN", mainScheme.name)
        assertEquals(41, mainScheme.mappings.size)

        // Verify Pan (Aim Look) Control in CUSTOM
        val panMapping = customScheme.mappings.find { it.type == MappingType.AIM_LOOK }
        assertNotNull("Pan (AIM_LOOK) control must be present in CUSTOM scheme", panMapping)
        assertEquals(0.7665f, panMapping!!.normalizedX, 0.001f)
        assertEquals(0.4826f, panMapping.normalizedY, 0.001f)
        assertEquals(1.51f, panMapping.sensitivity, 0.001f)
        assertEquals(0.9668874f, panMapping.sensitivityRatioY, 0.001f)

        // Verify Tap Control with Primary & Alternate Key in CUSTOM
        val gTapMapping = customScheme.mappings.find { it.keyLabel == "G" }
        assertNotNull("Tap control for 'G' must be present", gTapMapping)
        assertEquals(KeyEvent.KEYCODE_G, gTapMapping!!.keyCode)
        assertEquals("GamepadLeftShoulder + GamepadX", gTapMapping.altKeyLabel)

        // Verify TapRepeat Control
        val tapRepeatMapping = customScheme.mappings.find { it.type == MappingType.TAP_REPEAT }
        assertNotNull("TapRepeat control must be present", tapRepeatMapping)

        // Verify Dpad (WASD) Control
        val dpadMapping = customScheme.mappings.find { it.type == MappingType.WASD_STICK }
        assertNotNull("Dpad (WASD) control must be present", dpadMapping)
    }

    @Test
    fun testExportAndReImportRoundTrip_preservesSchemeDataAndControlCounts() {
        val sampleFile = File("C:/Users/snave/NaveedMushtaq/PersonalProjects/FreeFireKeyMap/com.dts.freefireth.cfg")
        val content = sampleFile.readText()

        // 1. Initial parse
        val initialParsed = KeymapCfgParser.parseCfgJson(content, "game-freefire-123")

        // 2. Export to BlueStacks .cfg format
        val exportedCfg = KeymapExportManager.exportToBlueStacksCfg(initialParsed.profiles)
        assertTrue("Exported CFG string must not be blank", exportedCfg.isNotBlank())

        // 3. Re-import exported CFG
        val reimported = KeymapCfgParser.parseCfgJson(exportedCfg, "game-freefire-123")
        assertEquals(2, reimported.profiles.size)

        val customReimported = reimported.profiles.find { it.name == "CUSTOM" }
        assertNotNull("Exported CUSTOM scheme must be present on re-import", customReimported)
        assertEquals(38, customReimported!!.mappings.size)

        val mainReimported = reimported.profiles.find { it.name == "MAIN" }
        assertNotNull("Exported MAIN scheme must be present on re-import", mainReimported)
        assertEquals(41, mainReimported!!.mappings.size)
    }

    @Test
    fun testKeyNameMapper_mapsKeyCodesAndNamesCorrectly() {
        assertEquals(KeyEvent.KEYCODE_W, KeyNameMapper.parseKeyNameToCode("W"))
        assertEquals(KeyEvent.KEYCODE_SPACE, KeyNameMapper.parseKeyNameToCode("Space"))
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, KeyNameMapper.parseKeyNameToCode("Ctrl"))
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, KeyNameMapper.parseKeyNameToCode("MouseLButton"))

        assertEquals("Space", KeyNameMapper.androidCodeToKeyName(KeyEvent.KEYCODE_SPACE))
        assertEquals("Mouse LClick", KeyNameMapper.androidCodeToKeyName(KeyEvent.KEYCODE_BUTTON_L1))
    }
}
