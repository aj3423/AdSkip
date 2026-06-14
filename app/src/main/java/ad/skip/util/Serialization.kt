package ad.skip.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val module = SerializersModule {
    polymorphic(IAction::class) {
        // Triggers
        // also add to above polymorphic(ITriggerAction:class)
        subclass(Click::class)
        subclass(Swipe::class)
    }
}

val json =  Json {
    this.serializersModule = module
    encodeDefaults = false
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}
