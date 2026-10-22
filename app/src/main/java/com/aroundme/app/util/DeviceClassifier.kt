package com.aroundme.app.util

import com.aroundme.app.data.model.Transport

object Category {
    const val UNKNOWN = 0
    const val ROUTER = 1
    const val TV = 2
    const val PHONE = 3
    const val WATCH = 4
    const val AUDIO = 5
    const val COMPUTER = 6
    const val BEACON = 7
    const val PRINTER = 8
    const val CAR = 9
    const val IOT = 10
    const val CAMERA = 11

    fun label(c: Int) = when (c) {
        ROUTER -> "router/AP"
        TV -> "TV"
        PHONE -> "phone"
        WATCH -> "watch"
        AUDIO -> "audio"
        COMPUTER -> "computer"
        BEACON -> "tag/beacon"
        PRINTER -> "printer"
        CAR -> "car"
        IOT -> "IoT"
        CAMERA -> "camera"
        else -> null
    }
}

object DeviceClassifier {

    private class Rule(val cat: Int, vararg val keywords: String)

    private val rules = listOf(
        Rule(Category.WATCH, "watch", "amazfit", "garmin", "fitbit", "ticwatch", "mi band", "galaxy fit", "band "),
        Rule(Category.AUDIO, "buds", "airpods", "earbud", "headphone", "headset", "speaker", "homepod", "sonos", "beats", "jbl", "soundcore", "wf-", "wh-", "freebuds", "echo dot", "echo-"),
        Rule(Category.TV, "bravia", "chromecast", "roku", "fire tv", "appletv", "apple tv", "android tv", "vizio", "tizen", "webos", "smart tv", "shield tv", "-tv", " tv", "tv-"),
        Rule(Category.CAR, "sync", "uconnect", "mybmw", "mercedes", "toyota", "tesla", "ford pass", "carplay", "car audio", "-car", "car-"),
        Rule(Category.CAMERA, "doorbell", "wyze", "gopro", "nvr", "dvr", "cam-", "-cam", "camera", "nest cam", "ring"),
        Rule(Category.PRINTER, "printer", "epson", "canon", "brother", "hp officejet", "hp laserjet", "deskjet", "envy"),
        Rule(Category.BEACON, "airtag", "tile", "smarttag", "smart tag", "chipolo", "galaxy tag", "findmy"),
        Rule(Category.IOT, "nest", "hue", "shelly", "tasmota", "sonoff", "esp32", "tuya", "plug", "lamp", "vacuum", "roborock", "roomba", "alexa", "wemo", "kasa", "blink", "ecobee", "thermostat", "lifx"),
        Rule(Category.PHONE, "iphone", "galaxy", "pixel", "redmi", "xiaomi", "oneplus", "huawei p", "mate ", "phone", "honor", "realme", "oppo", "vivo", "poco", "nothing phone"),
        Rule(Category.COMPUTER, "macbook", "imac", "laptop", "desktop", "-pc", "windows", "thinkpad", "surface"),
        Rule(Category.ROUTER, "router", "tplink", "tp-link", "tp_link", "netgear", "linksys", "ubnt", "unifi", "mikrotik", "dlink", "d-link", "fritz", "asus", "zyxel", "openwrt", "vodafone", "etisalat", "stc", "mobily", "zain", "virginmedia", "sky", "bt home", "homespot", "_ext", "repeater", "mesh", "deco", "orbi", "eero")
    )

    fun classify(transport: Int, name: String?): Int {
        val n = name?.lowercase() ?: ""
        if (n.isNotEmpty()) {
            for (r in rules) {
                if (r.keywords.any { n.contains(it) }) return r.cat
            }
        }
        return when (transport) {
            Transport.WIFI_AP -> Category.ROUTER
            else -> Category.UNKNOWN
        }
    }

    private val oui = mapOf(
        "3C:22:FB" to "Apple", "A4:83:E7" to "Apple", "F0:18:98" to "Apple",
        "8C:85:90" to "Apple", "AC:BC:32" to "Apple", "40:CB:C0" to "Apple",
        "78:7E:61" to "Apple", "A8:51:AB" to "Apple", "DC:A9:04" to "Apple",
        "F4:F1:5A" to "Apple", "18:AF:61" to "Apple", "34:C0:59" to "Apple",
        "A4:B1:97" to "Apple", "C0:84:7A" to "Apple", "70:56:81" to "Apple",
        "34:23:BA" to "Samsung", "50:32:75" to "Samsung", "84:25:DB" to "Samsung",
        "A8:7C:01" to "Samsung", "B8:57:D8" to "Samsung", "E4:7C:F9" to "Samsung",
        "F8:04:2E" to "Samsung", "CC:B1:1A" to "Samsung", "5C:0A:5B" to "Samsung",
        "F4:F5:D8" to "Google", "54:60:09" to "Google", "3C:5A:B4" to "Google",
        "A4:77:33" to "Google", "1C:F2:9A" to "Google", "94:EB:2C" to "Google",
        "50:EC:50" to "Xiaomi", "64:CC:2E" to "Xiaomi", "9C:99:A0" to "Xiaomi",
        "F8:A4:5F" to "Xiaomi", "28:6C:07" to "Xiaomi", "34:CE:00" to "Xiaomi",
        "48:DB:50" to "Huawei", "AC:85:3D" to "Huawei", "C8:D1:5E" to "Huawei",
        "E8:08:8B" to "Huawei", "34:6B:D3" to "Huawei", "00:E0:FC" to "Huawei",
        "50:C7:BF" to "TP-Link", "18:D6:C7" to "TP-Link", "98:DA:C4" to "TP-Link",
        "F4:F2:6D" to "TP-Link", "C0:25:E9" to "TP-Link", "B0:4E:26" to "TP-Link",
        "9C:D3:6D" to "Netgear", "20:E5:2A" to "Netgear", "A0:21:B7" to "Netgear",
        "10:0D:7F" to "Netgear", "28:80:88" to "Netgear",
        "74:75:48" to "Amazon", "F0:27:2D" to "Amazon", "68:54:FD" to "Amazon",
        "44:65:0D" to "Amazon", "FC:A1:83" to "Amazon", "40:B4:CD" to "Amazon",
        "24:6F:28" to "Espressif", "30:AE:A4" to "Espressif", "84:F7:03" to "Espressif",
        "3C:61:05" to "Espressif", "A4:CF:12" to "Espressif", "24:0A:C4" to "Espressif",
        "00:04:1F" to "Sony", "78:84:3C" to "Sony", "54:42:49" to "Sony",
        "20:3D:BD" to "LG", "40:B0:34" to "LG", "A8:16:B2" to "LG",
        "A4:34:D9" to "Intel", "3C:A9:F4" to "Intel", "80:32:53" to "Intel",
        "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi", "E4:5F:01" to "Raspberry Pi",
        "7C:1E:52" to "Microsoft", "28:18:78" to "Microsoft",
        "00:17:88" to "Philips Hue", "EC:B5:FA" to "Philips Hue",
        "18:B4:30" to "Nest", "64:16:66" to "Nest",
        "D8:EB:97" to "Tenda", "C8:3A:35" to "Tenda",
        "00:25:9C" to "Cisco-Linksys", "68:7F:74" to "Cisco-Linksys"
    )

    fun vendor(mac: String): String? =
        if (mac.length >= 8) oui[mac.substring(0, 8).uppercase()] else null
}
