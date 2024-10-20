package net.minestom.server.recipe.display;

import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.registry.StaticProtocolObject;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.utils.nbt.BinaryTagSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * AUTOGENERATED by GenericEnumGenerator
 */
public enum SlotDisplayType implements StaticProtocolObject {
    EMPTY(NamespaceID.from("minecraft:empty")),

    ANY_FUEL(NamespaceID.from("minecraft:any_fuel")),

    ITEM(NamespaceID.from("minecraft:item")),

    ITEM_STACK(NamespaceID.from("minecraft:item_stack")),

    TAG(NamespaceID.from("minecraft:tag")),

    SMITHING_TRIM(NamespaceID.from("minecraft:smithing_trim")),

    WITH_REMAINDER(NamespaceID.from("minecraft:with_remainder")),

    COMPOSITE(NamespaceID.from("minecraft:composite"));

    public static final NetworkBuffer.Type<SlotDisplayType> NETWORK_TYPE = NetworkBuffer.Enum(SlotDisplayType.class);

    public static final BinaryTagSerializer<SlotDisplayType> NBT_TYPE = BinaryTagSerializer.fromEnumKeyed(SlotDisplayType.class);

    private final NamespaceID namespace;

    SlotDisplayType(@NotNull NamespaceID namespace) {
        this.namespace = namespace;
    }

    @NotNull
    @Override
    public NamespaceID namespace() {
        return this.namespace;
    }

    @Override
    public int id() {
        return this.ordinal();
    }
}
