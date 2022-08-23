package com.emberjs.glint

import GlintRunner
import com.jetbrains.rd.util.first
import org.junit.Test
import kotlin.test.assertEquals

class GlintTest {

    var glintText = """
        10:33:52 - Starting compilation in watch mode...

        addon/components/button/template.hbs:1:1 - error TS2769: The given value does not appear to be usable as a component, modifier or helper.
          No overload matches this call.
            The last overload gave the following error.
              Argument of type 'void | DebuggerKeyword | HasBlockKeyword | HasBlockParamsKeyword | InElementKeyword | LetKeyword | ... 19 more ... | typeof CarbonIcon' is not assignable to parameter of type '(...positional: unknown[]) => unknown'.
                Type 'void' is not assignable to type '(...positional: unknown[]) => unknown'.

        1 {{import Loading from '../loading'}}
          ~~~~~~~~~~~~~~~

          node_modules/@glint/environment-ember-loose/-private/dsl/index.d.ts:33:25
            33 export declare function resolve<P extends unknown[], T>(
                                       ~~~~~~~
            The last overload is declared here.

        addon/components/button/template.hbs:1:19 - error TS2769: No overload matches this call.
          The last overload gave the following error.
            Argument of type 'string' is not assignable to parameter of type 'abstract new (...args: unknown[]) => Invokable<(args: unknown) => AcceptsBlocks<any, any>>'.

        1 {{import Loading from '../loading'}}
                            ~~~~~~~~~~~~~~~~~~
        2 {{import or from 'ember-truth-helpers/helpers/or'}}
          ~~~~~~~

          node_modules/@glint/environment-ember-loose/-private/intrinsics/component.d.ts:64:3
             64   <
                  ~
             65     Args,
                ~~~~~~~~~
            ...
             74       | undefined
                ~~~~~~~~~~~~~~~~~
             75   ): null | (abstract new () => PartiallyAppliedComponent<Args, GivenArgs, Return>);
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            The last overload is declared here.

        addon/components/button/template.hbs:1:19 - error TS2769: No overload matches this call.
          The last overload gave the following error.
            Argument of type 'string' is not assignable to parameter of type 'abstract new (...args: unknown[]) => Invokable<(args: unknown) => AcceptsBlocks<any, any>>'.

        1 {{import Loading from '../loading'}}
                            ~~~~~~~~~~~~~~~~~~
        2 {{import or from 'ember-truth-helpers/helpers/or'}}
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

          node_modules/@glint/environment-ember-loose/-private/intrinsics/component.d.ts:64:3
             64   <
                  ~
             65     Args,
                ~~~~~~~~~
            ...
             74       | undefined
                ~~~~~~~~~~~~~~~~~
             75   ): null | (abstract new () => PartiallyAppliedComponent<Args, GivenArgs, Return>);
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            The last overload is declared here.

        addon/components/button/template.hbs:6:9 - error TS2345: Argument of type 'HTMLButtonElement' is not assignable to parameter of type 'HTMLBaseElement'.
          Type 'HTMLButtonElement' is missing the following properties from type 'HTMLBaseElement': href, target

        6 <button ...attributes
                  ~~~~~~~~~~~~~
        7         onclick={{this.onButtonClick}}
          ~~~~~~~~

        addon/components/button/template.hbs:25:10 - error TS2339: Property 'label' does not exist on type 'Args'.

        25       {{@label}}
                    ~~~~~


        10:33:59 - Found 5 errors. Watching for file changes.

    """.trimIndent()
    @Test fun testGlintText() {
        GlintRunner.parseGlintText(glintText)
        assertEquals(1, GlintRunner.cache.count(), "should have 1 file")
        assertEquals(5, GlintRunner.cache.first().value.size, "should have 5 errors")
    }
}
