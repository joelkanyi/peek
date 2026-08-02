package io.github.joelkanyi.peek.core.codec

import io.github.joelkanyi.peek.core.model.KvEntry
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
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

    override fun encode(snapshot: StoreSnapshot): ByteString {
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
        sb.append("<map>\n")
        for (entry in snapshot.entries) {
            val name = xmlEscape(entry.key)
            when (val v = entry.value) {
                is KvValue.StringValue -> sb.append("    <string name=\"$name\">${xmlEscape(v.value)}</string>\n")
                is KvValue.IntValue -> sb.append("    <int name=\"$name\" value=\"${v.value}\" />\n")
                is KvValue.LongValue -> sb.append("    <long name=\"$name\" value=\"${v.value}\" />\n")
                is KvValue.FloatValue -> sb.append("    <float name=\"$name\" value=\"${v.value}\" />\n")
                is KvValue.BoolValue -> sb.append("    <boolean name=\"$name\" value=\"${v.value}\" />\n")
                is KvValue.StringSetValue -> {
                    sb.append("    <set name=\"$name\">\n")
                    v.values.forEach { sb.append("        <string>${xmlEscape(it)}</string>\n") }
                    sb.append("    </set>\n")
                }
                else -> throw UnsupportedOperationException("value type not supported in SharedPreferences: ${v::class.simpleName}")
            }
        }
        sb.append("</map>\n")
        return sb.toString().encodeUtf8()
    }

    private fun xmlEscape(text: String): String = buildString {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
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
