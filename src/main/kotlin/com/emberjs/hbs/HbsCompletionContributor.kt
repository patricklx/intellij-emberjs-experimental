package com.emberjs.hbs

import com.emberjs.hbs.HbsPatterns.BLOCK_MUSTACHE_NAME_ID
import com.emberjs.hbs.HbsPatterns.BLOCK_MUSTACHE_PARAM
import com.emberjs.hbs.HbsPatterns.IMPORT_NAMES
import com.emberjs.hbs.HbsPatterns.IMPORT_PATH_AUTOCOMPLETE
import com.emberjs.hbs.HbsPatterns.MUSTACHE_ID
import com.emberjs.hbs.HbsPatterns.MUSTACHE_ID_MISSING
import com.emberjs.hbs.HbsPatterns.SIMPLE_MUSTACHE_NAME_ID
import com.emberjs.hbs.HbsPatterns.STRING_PARAM_OTHER
import com.emberjs.hbs.HbsPatterns.SUB_EXPR_NAME_ID
import com.emberjs.hbs.HbsPatterns.inXmlTag
import com.emberjs.hbs.Internals.InternalsWithBlock
import com.emberjs.hbs.Internals.InternalsWithoutBlock
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType



/**
 * The `HbsCompletionContributor` class is responsible for registering all
 * available `CompletionProviders` for the Handlebars language to their
 * corresponding `PsiElementPatterns`.
 */
class HbsCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, SIMPLE_MUSTACHE_NAME_ID, HbsBuiltinHelperCompletionProvider(InternalsWithoutBlock))
        extend(CompletionType.BASIC, BLOCK_MUSTACHE_NAME_ID, HbsBuiltinHelperCompletionProvider(InternalsWithBlock))
        extend(CompletionType.BASIC, SUB_EXPR_NAME_ID, HbsBuiltinHelperCompletionProvider(InternalsWithoutBlock))

        extend(CompletionType.BASIC, SIMPLE_MUSTACHE_NAME_ID, HbsLocalCompletion())
        extend(CompletionType.BASIC, BLOCK_MUSTACHE_NAME_ID, HbsLocalCompletion())
        extend(CompletionType.BASIC, SUB_EXPR_NAME_ID, HbsLocalCompletion())
        extend(CompletionType.BASIC, MUSTACHE_ID, HbsLocalCompletion())
        extend(CompletionType.BASIC, IMPORT_NAMES, HbsLocalCompletion())
        extend(CompletionType.BASIC, IMPORT_PATH_AUTOCOMPLETE, HbsLocalCompletion())
        extend(CompletionType.BASIC, BLOCK_MUSTACHE_PARAM, HbsLocalCompletion())
        extend(CompletionType.BASIC, MUSTACHE_ID_MISSING, HbsLocalCompletion())
        extend(CompletionType.BASIC, STRING_PARAM_OTHER, HbsLocalCompletion())
        extend(CompletionType.BASIC, inXmlTag, HbsLocalCompletion())
    }
}
