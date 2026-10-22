package com.aroundme.app.scan

object BleAdvParser {

    data class Info(
        val name: String?,
        val appearance: Int?,
        val companyId: Int?,
        val beacon: Boolean
    )

    fun parse(bytes: ByteArray?): Info {
        if (bytes == null) return Info(null, null, null, false)
        var i = 0
        var name: String? = null
        var appearance: Int? = null
        var companyId: Int? = null
        var beacon = false
        while (i + 1 < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0) break
            val type = bytes[i + 1].toInt() and 0xFF
            val start = i + 2
            val end = (start + len - 1).coerceAtMost(bytes.size)
            when (type) {
                0x08, 0x09 -> name = runCatching {
                    String(bytes, start, end - start).trim()
                }.getOrNull()

                0x19 -> if (end - start >= 2) {
                    appearance = (bytes[start].toInt() and 0xFF) or
                        ((bytes[start + 1].toInt() and 0xFF) shl 8)
                }

                0x03, 0x02 -> {
                    var j = start
                    while (j + 1 < end) {
                        if ((bytes[j].toInt() and 0xFF) == 0xAA &&
                            (bytes[j + 1].toInt() and 0xFF) == 0xFE
                        ) beacon = true
                        j += 2
                    }
                }

                0xFF -> if (end - start >= 2) {
                    companyId = (bytes[start].toInt() and 0xFF) or
                        ((bytes[start + 1].toInt() and 0xFF) shl 8)
                    if (companyId == 0x004C && end - start >= 4 &&
                        (bytes[start + 2].toInt() and 0xFF) == 0x02 &&
                        (bytes[start + 3].toInt() and 0xFF) == 0x15
                    ) beacon = true
                }
            }
            i += len + 1
        }
        return Info(name, appearance, companyId, beacon)
    }
}
