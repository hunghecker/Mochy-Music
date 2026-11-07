package com.mochyy.mochymusic.importer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class MetadataTextRepairTest {
    @Test fun repairsWesternUtf8Mojibake() {
        assertEquals("Võ Văn Huy", MetadataTextRepair.repair("VÃµ VÄƒn Huy"))
        assertEquals("Anh Là Ai", MetadataTextRepair.repair("Anh LÃ  Ai"))
    }
    @Test fun repairsMixedChineseMojibake() {
        for (text in listOf("Anh Là Ai", "Anh Không Nói Thật", "Võ Văn Huy")) {
            val broken = String(text.toByteArray(Charsets.UTF_8), Charset.forName("GB18030"))
            assertEquals(text, MetadataTextRepair.repair(broken))
        }
    }
    @Test fun repairsVietnameseLegacyEncoding() {
        val text = "Anh Là Ai"
        assertEquals(text, MetadataTextRepair.repair(String(text.toByteArray(Charsets.UTF_8), Charset.forName("windows-1258"))))
    }
    @Test fun preservesValidMultilingualMetadata() {
        for (text in listOf("Võ Văn Huy", "Anh Không Nói Thật", "张学友", "夜に駆ける", "Beyoncé", "AC/DC", "Ăn cơm", "", "Nhạc Việt 中文")) {
            assertEquals(text, MetadataTextRepair.repair(text))
        }
    }
    @Test fun neverGuessesLostCharacters() {
        assertEquals("Anh \uFFFD Ai", MetadataTextRepair.repair("Anh \uFFFD Ai"))
    }
    @Test fun repairIsIdempotent() {
        val once = MetadataTextRepair.repair("VÃµ VÄƒn Huy")
        assertEquals(once, MetadataTextRepair.repair(once))
    }
}
