package io.github.joelkanyi.peek.core.codec

import io.github.joelkanyi.peek.core.model.KvEntry
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * Decodes SharedPreferences XML:
 *
 * ```
 * <map>
 *   <string name="user">joel</string>
 *   <int name="count" value="3" />
 *   <long name="ts" value="1712" />
 *   <float name="ratio" value="0.5" />
 *   <boolean name="flag" value="true" />
 *   <set name="tags"><string>a</string><string>b</string></set>
 * </map>
 * ```
 *
 * Scalars carry a `value` attribute; strings carry element text; sets contain
 * `<string>` children. Document order is preserved.
 */
public class SharedPreferencesXmlCodec : StoreCodec {

    override val type: StoreType = StoreType.SHARED_PREFERENCES

    override fun decode(handle: StoreHandle, bytes: ByteString, capturedAtEpochMs: Long): DecodeResult {
        return try {
            val reader = newFactory().createXMLStreamReader(bytes.toByteArray().inputStream(), "UTF-8")
            val entries = ArrayList<KvEntry>()
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) continue
                    when (val name = reader.localName) {
                        "map" -> Unit // document root
                        "string" -> entries.add(KvEntry(nameAttr(reader), KvValue.StringValue(reader.elementText)))
                        "int" -> entries.add(KvEntry(nameAttr(reader), KvValue.IntValue(valueAttr(reader).toInt())))
                        "long" -> entries.add(KvEntry(nameAttr(reader), KvValue.LongValue(valueAttr(reader).toLong())))
                        "float" -> entries.add(KvEntry(nameAttr(reader), KvValue.FloatValue(valueAttr(reader).toFloat())))
                        "boolean" -> entries.add(KvEntry(nameAttr(reader), KvValue.BoolValue(valueAttr(reader).toBooleanStrict())))
                        "set" -> entries.add(KvEntry(nameAttr(reader), KvValue.StringSetValue(readSet(reader))))
                        else -> error("unsupported element '$name'")
                    }
                }
            } finally {
                reader.close()
            }
            DecodeResult.Decoded(StoreSnapshot(handle, entries, capturedAtEpochMs))
        } catch (e: Exception) {
            DecodeResult.Failed(reason = e.message ?: "unparseable SharedPreferences XML", bytes = bytes)
        }
    }

    private fun readSet(reader: XMLStreamReader): Set<String> {
        val values = LinkedHashSet<String>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.localName != "string") error("unexpected <${reader.localName}> inside <set>")
                    values.add(reader.elementText)
                }
                XMLStreamConstants.END_ELEMENT -> if (reader.localName == "set") return values
                else -> Unit
            }
        }
        return values
    }

    private fun nameAttr(reader: XMLStreamReader): String =
        reader.getAttributeValue(null, "name") ?: error("<${reader.localName}> missing name attribute")

    private fun valueAttr(reader: XMLStreamReader): String =
        reader.getAttributeValue(null, "value") ?: error("<${reader.localName}> missing value attribute")

    private fun newFactory(): XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }
}
