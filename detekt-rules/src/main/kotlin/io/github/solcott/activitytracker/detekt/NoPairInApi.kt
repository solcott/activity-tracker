package io.github.solcott.activitytracker.detekt

import com.intellij.psi.PsiElement
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * `Pair` and `Triple` stay out of every non-private signature: a public or internal function's parameters, receiver and result, a
 * property's type, a primary constructor's parameters and a type alias. Their `first` and `second` say nothing about what the values are,
 * and Swift export can't bridge them inside a collection, so the values get a data class whose properties name them. Private declarations
 * (and members of private classes), locals and tests may keep them. An `override` is left to the declaration it overrides.
 *
 * Plain analysis, without types: it reads the written types and, for a declaration without one, an expression body or initializer that is
 * plainly a pair (`a to b`, `Pair(…)`, `Triple(…)`, or a call whose type arguments name one). Test source sets (`src/test`, `src/jvmTest`,
 * `src/screenshotTestDebug`…) and test support modules (`*-testing`) are skipped, told apart by the file's absolute path, which every
 * detekt task sees the same.
 */
class NoPairInApi(config: Config) :
    Rule(
        config,
        "Pair and Triple stay out of non-private signatures: a data class names what the values are, and Swift export can bridge it.",
    ) {
    override fun visitKtFile(file: KtFile) {
        if (!isTestSource(file.virtualFilePath)) super.visitKtFile(file)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isExposed()) return
        val inputs = function.valueParameters.mapNotNull { it.typeReference } + listOfNotNull(function.receiverTypeReference)
        inputs.filter { it.namesPair() }.forEach { report(it, "${function.name}'s parameters") }
        val result = function.typeReference
        when {
            result != null -> if (result.namesPair()) report(result, "${function.name}'s result")
            !function.hasBlockBody() ->
                function.bodyExpression?.takeIf { it.isPlainlyPair() }?.let { report(it, "${function.name}'s result") }
        }
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (!property.isExposed()) return
        property.receiverTypeReference?.takeIf { it.namesPair() }?.let { report(it, "${property.name}'s receiver") }
        val type = property.typeReference
        val body = property.initializer ?: property.getter?.takeIf { !it.hasBlockBody() }?.bodyExpression
        when {
            type != null -> if (type.namesPair()) report(type, "${property.name}'s type")
            body != null && body.isPlainlyPair() -> report(body, "${property.name}'s type")
        }
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        super.visitPrimaryConstructor(constructor)
        val owner = constructor.getContainingClassOrObject()
        if (constructor.hasModifier(KtTokens.PRIVATE_KEYWORD) || !owner.isVisible()) return
        constructor.valueParameters
            .mapNotNull { it.typeReference }
            .filter { it.namesPair() }
            .forEach { report(it, "${owner.name}'s constructor") }
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        super.visitTypeAlias(typeAlias)
        if (!typeAlias.isExposed()) return
        typeAlias.getTypeReference()?.takeIf { it.namesPair() }?.let { report(it, "Type alias ${typeAlias.name}") }
    }

    private fun report(element: PsiElement, where: String) {
        report(
            Finding(
                Entity.from(element),
                "$where name a Pair or Triple. Use a data class whose properties say what the values are, or make the declaration private.",
            ),
        )
    }
}

private val PAIR = Regex("""\b(Pair|Triple)\b""")

private fun KtTypeReference.namesPair(): Boolean = PAIR.containsMatchIn(text)

/** `a to b`, `Pair(…)`, `Triple(…)`, or a call such as `listOf<Pair<…>>()` whose type arguments name one. */
private fun KtExpression.isPlainlyPair(): Boolean =
    when (this) {
        is KtBinaryExpression -> operationReference.getReferencedName() == "to"
        is KtCallExpression ->
            calleeExpression?.text in setOf("Pair", "Triple", "kotlin.Pair", "kotlin.Triple") ||
                typeArguments.any { it.typeReference?.namesPair() == true }
        else -> false
    }

/** Seen outside its file: top level or a member, not private or an override, and in no private or local class. */
private fun KtDeclaration.isExposed(): Boolean {
    if (hasModifier(KtTokens.PRIVATE_KEYWORD) || hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
    return when (val container = parent) {
        is KtFile -> true
        is KtClassBody -> (container.parent as? KtClassOrObject)?.isVisible() ?: false
        else -> false
    }
}

/** Neither the class nor any class around it is private or local. */
private fun KtClassOrObject.isVisible(): Boolean =
    generateSequence(this) { (it.parent as? KtClassBody)?.parent as? KtClassOrObject }
        .none { it.hasModifier(KtTokens.PRIVATE_KEYWORD) || it.isLocal } && (parent is KtFile || parent is KtClassBody)

/**
 * In a test source set (the directory after `src` is `test` or names one: `commonTest`, `jvmTest`, `screenshotTestDebug`…) or in a test
 * support module, whose directory ends in `-testing` (`app/common/presenter-testing`).
 */
internal fun isTestSource(path: String): Boolean {
    val parts = path.replace('\\', '/').split('/')
    val src = parts.indexOf("src")
    val set = parts.getOrNull(src + 1).takeIf { src >= 0 }.orEmpty()
    return set == "test" || set.contains("Test") || (src > 0 && parts[src - 1].endsWith("-testing"))
}
