package cl.baske.tv

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform