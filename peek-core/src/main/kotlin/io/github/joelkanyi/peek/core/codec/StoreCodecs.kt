package io.github.joelkanyi.peek.core.codec

import io.github.joelkanyi.peek.core.model.StoreType

/** The codec for each store type. Shared by the session and by capture diffing. */
public object StoreCodecs {
    public fun codecFor(type: StoreType): StoreCodec = when (type) {
        StoreType.SHARED_PREFERENCES -> SharedPreferencesXmlCodec()
        StoreType.PREFERENCES_DATASTORE -> PreferencesPbCodec()
        StoreType.PROTO_DATASTORE -> ProtoDataStoreCodec()
    }
}
