// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.sdk.language;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.intellij.sdk.language.psi.SimpleTypes;
import com.intellij.psi.TokenType;

%%

%class GtdLexer
%implements GtdLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

TEMPLATE_START=<template
TEMPLATE_END=<\/template>
ANY=.


%state IN_TEMPLATE

%%

<YYINITIAL> {TEMPLATE_START}                           { yybegin(IN_TEMPLATE); return GtsTokenTypes.TEMPLATE; }
<IN_TEMPLATE> {
    {TEMPLATE_END}                                     { yybegin(YYINITIAL); return GtsTokenTypes.TEMPLATE; }
    {ANY}                                              { yybegin(IN_TEMPLATE); return GtsTokenTypes.TEMPLATE; }
}

[^]                                                    { yybegin(YYINITIAL); return GtsTokenTypes.JS; }