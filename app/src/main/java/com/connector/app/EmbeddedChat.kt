package com.connector.app

internal object EmbeddedChat {
    val GZ_B64_PARTS: Array<String>
        get() = EmbeddedChatPart1.PARTS + EmbeddedChatPart2.PARTS
}
