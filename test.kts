fun generateToken(seedString: String, length: Int = 5): String {
    var hash: Int = 0
    for (i in seedString.indices) {
        hash = (hash * 31) + seedString[i].code
    }
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val result = StringBuilder()
    var randomSeed: Long = hash.toLong() and 0xFFFFFFFFL
    for (i in 0 until length) {
        randomSeed = (randomSeed * 1103515245L + 12345L) and 0xFFFFFFFFL
        result.append(chars[(randomSeed % chars.length).toInt()])
    }
    return result.toString()
}

println("Kotlin Token: " + generateToken("PARAMARTHA_SECRET_12345_MASUK"))
