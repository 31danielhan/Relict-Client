package relict.core

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import relict.RelictClient

object Keys {
    /** Shown as "Relict" in Controls; the label comes from key.category.relict.main in the lang file. */
    private val category: KeyMapping.Category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RelictClient.MOD_ID, "main"))

    fun register(translationKey: String, defaultKey: Int = KeyCodes.UNBOUND): KeyHandle {
        val mapping = KeyMappingHelper.registerKeyMapping(KeyMapping(translationKey, defaultKey, category))
        return KeyHandle(mapping, mapping::consumeClick) { mapping.translatedKeyMessage.string }
    }
}
