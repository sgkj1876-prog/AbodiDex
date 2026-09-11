package com.abodi.dex

import android.content.Context
import android.net.Uri
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.Instruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.Reference
import org.jf.dexlib2.iface.reference.StringReference
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

class DexAnalyzer(private val context: Context) {
    data class MethodInfo(
        val key: String,
        val className: String,
        val name: String,
        val signature: String,
        val dexName: String,
        val bytecode: List<String>
    )

    data class Xref(
        val kind: Kind,
        val fromMethod: String,
        val fromClass: String,
        val target: String,
        val dexName: String
    ) {
        enum class Kind { STRING, METHOD_CALL, FIELD }
    }

    data class Hit(
        val severity: Int,
        val text: String,
        val detail: String,
        val method: String,
        val dex: String
    )

    data class Summary(
        val dexCount: Int,
        val classes: Int,
        val methods: Int,
        val strings: Int,
        val hits: List<Hit>,
        val methodsList: List<MethodInfo>,
        val xrefs: List<Xref>
    )

    private val needles = listOf(
        "PurchaseState", "PURCHASED", "purchase_token", "inapp_purchase", "ispro",
        "product_id", "ProductType", "BillingClient", "purchase", "subscription"
    )

    fun analyze(uri: Uri, progress: (Int, String) -> Unit): Summary {
        val apk = File.createTempFile("abodi_", ".apk", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "تعذر فتح الملف" }
                apk.outputStream().use { output -> input.copyTo(output) }
            }

            val hits = mutableListOf<Hit>()
            val methodsList = mutableListOf<MethodInfo>()
            val xrefs = mutableListOf<Xref>()
            var dexCount = 0
            var classes = 0
            var methods = 0
            var strings = 0

            ZipInputStream(FileInputStream(apk)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.matches(Regex("classes(\\d*)\\.dex"))) {
                        dexCount++
                        val data = zis.readBytes()
                        val dexFile = DexBackedDexFile(Opcodes.forApi(35), data)
                        classes += dexFile.classes.size
                        strings += dexFile.stringSection.size

                        for (clazz in dexFile.classes) {
                            for (method in clazz.methods) {
                                methods++
                                val methodKey = methodKey(method)
                                val bytecode = buildBytecode(method)
                                if (methodsList.size < MAX_METHODS) {
                                    methodsList += MethodInfo(
                                        key = methodKey,
                                        className = method.definingClass,
                                        name = method.name,
                                        signature = methodSignature(method),
                                        dexName = entry.name,
                                        bytecode = bytecode
                                    )
                                }
                                val implementation = method.implementation ?: continue
                                for (instruction in implementation.instructions) {
                                    val reference = referenceOf(instruction) ?: continue
                                    val fromClass = method.definingClass
                                    val fromMethod = methodKey
                                    when (reference) {
                                        is StringReference -> {
                                            val text = reference.string
                                            xrefs += Xref(Xref.Kind.STRING, fromMethod, fromClass, text, entry.name)
                                            val lower = text.lowercase()
                                            val needle = needles.firstOrNull { lower.contains(it.lowercase()) }
                                            if (needle != null) {
                                                val severity = when (needle.lowercase()) {
                                                    "purchasestate", "purchased" -> 90
                                                    "purchase_token", "inapp_purchase" -> 86
                                                    "product_id", "producttype", "billingclient" -> 78
                                                    else -> 60
                                                }
                                                hits += Hit(severity, text, "String Caller / XREF", fromMethod, entry.name)
                                            }
                                        }
                                        is MethodReference -> xrefs += Xref(
                                            Xref.Kind.METHOD_CALL,
                                            fromMethod,
                                            fromClass,
                                            methodReferenceText(reference),
                                            entry.name
                                        )
                                        is FieldReference -> xrefs += Xref(
                                            Xref.Kind.FIELD,
                                            fromMethod,
                                            fromClass,
                                            fieldReferenceText(reference),
                                            entry.name
                                        )
                                    }
                                }
                            }
                        }
                        progress((dexCount * 100).coerceAtMost(95), "تحليل ${entry.name}…")
                    }
                }
            }

            progress(100, "اكتمل التحليل")
            return Summary(
                dexCount = dexCount,
                classes = classes,
                methods = methods,
                strings = strings,
                hits = hits.distinctBy { "${it.method}|${it.text}" }.sortedByDescending { it.severity }.take(MAX_HITS),
                methodsList = methodsList,
                xrefs = xrefs.distinctBy { "${it.kind}|${it.fromMethod}|${it.target}|${it.dexName}" }.take(MAX_XREFS)
            )
        } finally {
            apk.delete()
        }
    }

    private fun methodKey(method: Method): String =
        "${method.definingClass}->${method.name}${methodSignature(method)}"

    private fun methodSignature(method: Method): String =
        "(${method.parameterTypes.joinToString("")})${method.returnType}"

    private fun methodReferenceText(reference: MethodReference): String =
        "${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"

    private fun fieldReferenceText(reference: FieldReference): String =
        "${reference.definingClass}->${reference.name}:${reference.type}"

    private fun referenceOf(instruction: Instruction): Reference? =
        (instruction as? ReferenceInstruction)?.reference

    private fun buildBytecode(method: Method): List<String> {
        val implementation = method.implementation ?: return listOf("// لا يوجد implementation لهذه الدالة")
        val result = mutableListOf<String>()
        var address = 0
        for (instruction in implementation.instructions) {
            val op = instruction.opcode.name
            result += String.format("%04x  %s", address, instruction.toString().ifBlank { op })
            address += 1
            if (result.size >= MAX_INSTRUCTIONS_PER_METHOD) {
                result += "… تم اختصار التعليمات لهذا العرض"
                break
            }
        }
        return result
    }

    companion object {
        private const val MAX_METHODS = 12000
        private const val MAX_HITS = 3000
        private const val MAX_XREFS = 20000
        private const val MAX_INSTRUCTIONS_PER_METHOD = 500
    }
}
