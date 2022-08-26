interface ArgsSetMap extends WeakMap<Args<unknown>, boolean> {
    get<S>(key: Args<S>): boolean | undefined;
    set<S>(key: Args<S>, value: boolean): this;
}
export declare let ARGS_SET: ArgsSetMap;
declare const Empty: unique symbol;
/**
 * This provides us a way to have a "fallback" which represents an empty object,
 * without the downsides of how TS treats `{}`. Specifically: this will
 * correctly leverage "excess property checking" so that, given a component
 * which has no named args, if someone invokes it with any named args, they will
 * get a type error.
 *
 * @internal This is exported so declaration emit works (if it were not emitted,
 *   declarations which fall back to it would not work). It is *not* intended for
 *   public usage, and the specific mechanics it uses may change at any time.
 *   The location of this export *is* part of the public API, because moving it
 *   will break existing declarations, but is not legal for end users to import
 *   themselves, so ***DO NOT RELY ON IT***.
 */
export declare type EmptyObject = {
    [Empty]?: true;
};
declare type GetOrElse<Obj, K extends PropertyKey, Fallback> = Obj extends {
    [Key in K]: infer U;
} ? U : Fallback;
/** Given a signature `S`, get back the `Args` type. */
declare type ArgsFor<S> = S extends {
    Args: infer Args;
} ? Args extends {
    Named?: object;
    Positional?: unknown[];
} ? {
    Named: GetOrElse<S['Args'], 'Named', EmptyObject>;
    Positional: GetOrElse<S['Args'], 'Positional', []>;
} : {
    Named: S['Args'];
    Positional: [];
} : {
    Named: EmptyObject;
    Positional: [];
};
declare type _ExpandSignature<T> = {
    Element: GetOrElse<T, 'Element', null>;
    Args: keyof T extends 'Args' | 'Element' | 'Blocks' ? ArgsFor<T> : {
        Named: T;
        Positional: [];
    };
    Blocks: T extends {
        Blocks: infer Blocks;
    } ? {
        [Block in keyof Blocks]: Blocks[Block] extends unknown[] ? {
            Params: {
                Positional: Blocks[Block];
            };
        } : Blocks[Block];
    } : EmptyObject;
};
/**
 * Given any allowed shorthand form of a signature, desugars it to its full
 * expanded type.
 *
 * @internal This is only exported so we can avoid duplicating it in
 *   [Glint](https://github.com/typed-ember/glint) or other such tooling. It is
 *   *not* intended for public usage, and the specific mechanics it uses may
 *   change at any time. Although the signature produced by is part of Glimmer's
 *   public API the existence and mechanics of this specific symbol are *not*,
 *   so ***DO NOT RELY ON IT***.
 */
export declare type ExpandSignature<T> = T extends any ? _ExpandSignature<T> : never;
/**
 * @internal we use this type for convenience internally; inference means users
 *   should not normally need to name it
 */
export declare type Args<S> = ExpandSignature<S>['Args']['Named'];
/**
 * The `Component` class defines an encapsulated UI element that is rendered to
 * the DOM. A component is made up of a template and, optionally, this component
 * object.
 *
 * ## Defining a Component
 *
 * To define a component, subclass `Component` and add your own properties,
 * methods and lifecycle hooks:
 *
 * ```ts
 * import Component from '@glimmer/component';
 *
 * export default class extends Component {
 * }
 * ```
 *
 * ## Lifecycle Hooks
 *
 * Lifecycle hooks allow you to respond to changes to a component, such as when
 * it gets created, rendered, updated or destroyed. To add a lifecycle hook to a
 * component, implement the hook as a method on your component subclass.
 *
 * For example, to be notified when Glimmer has rendered your component so you
 * can attach a legacy jQuery plugin, implement the `didInsertElement()` method:
 *
 * ```ts
 * import Component from '@glimmer/component';
 *
 * export default class extends Component {
 *   didInsertElement() {
 *     $(this.element).pickadate();
 *   }
 * }
 * ```
 *
 * ## Data for Templates
 *
 * `Component`s have two different kinds of data, or state, that can be
 * displayed in templates:
 *
 * 1. Arguments
 * 2. Properties
 *
 * Arguments are data that is passed in to a component from its parent
 * component. For example, if I have a `UserGreeting` component, I can pass it
 * a name and greeting to use:
 *
 * ```hbs
 * <UserGreeting @name="Ricardo" @greeting="Olá" />
 * ```
 *
 * Inside my `UserGreeting` template, I can access the `@name` and `@greeting`
 * arguments that I've been given:
 *
 * ```hbs
 * {{@greeting}}, {{@name}}!
 * ```
 *
 * Arguments are also available inside my component:
 *
 * ```ts
 * console.log(this.args.greeting); // prints "Olá"
 * ```
 *
 * Properties, on the other hand, are internal to the component and declared in
 * the class. You can use properties to store data that you want to show in the
 * template, or pass to another component as an argument.
 *
 * ```ts
 * import Component from '@glimmer/component';
 *
 * export default class extends Component {
 *   user = {
 *     name: 'Robbie'
 *   }
 * }
 * ```
 *
 * In the above example, we've defined a component with a `user` property that
 * contains an object with its own `name` property.
 *
 * We can render that property in our template:
 *
 * ```hbs
 * Hello, {{user.name}}!
 * ```
 *
 * We can also take that property and pass it as an argument to the
 * `UserGreeting` component we defined above:
 *
 * ```hbs
 * <UserGreeting @greeting="Hello" @name={{user.name}} />
 * ```
 *
 * ## Arguments vs. Properties
 *
 * Remember, arguments are data that was given to your component by its parent
 * component, and properties are data your component has defined for itself.
 *
 * You can tell the difference between arguments and properties in templates
 * because arguments always start with an `@` sign (think "A is for arguments"):
 *
 * ```hbs
 * {{@firstName}}
 * ```
 *
 * We know that `@firstName` came from the parent component, not the current
 * component, because it starts with `@` and is therefore an argument.
 *
 * On the other hand, if we see:
 *
 * ```hbs
 * {{name}}
 * ```
 *
 * We know that `name` is a property on the component. If we want to know where
 * the data is coming from, we can go look at our component class to find out.
 *
 * Inside the component itself, arguments always show up inside the component's
 * `args` property. For example, if `{{@firstName}}` is `Tom` in the template,
 * inside the component `this.args.firstName` would also be `Tom`.
 */
export default class BaseComponent<S = unknown> {
    /**
     * Constructs a new component and assigns itself the passed properties. You
     * should not construct new components yourself. Instead, Glimmer will
     * instantiate new components automatically as it renders.
     *
     * @param owner
     * @param args
     */
    constructor(owner: unknown, args: Args<S>);
    /**
     * Named arguments passed to the component from its parent component.
     * They can be accessed in JavaScript via `this.args.argumentName` and in the template via `@argumentName`.
     *
     * Say you have the following component, which will have two `args`, `firstName` and `lastName`:
     *
     * ```hbs
     * <my-component @firstName="Arthur" @lastName="Dent" />
     * ```
     *
     * If you needed to calculate `fullName` by combining both of them, you would do:
     *
     * ```ts
     * didInsertElement() {
     *   console.log(`Hi, my full name is ${this.args.firstName} ${this.args.lastName}`);
     * }
     * ```
     *
     * While in the template you could do:
     *
     * ```hbs
     * <p>Welcome, {{@firstName}} {{@lastName}}!</p>
     * ```
     */
    args: Readonly<Args<S>>;
    readonly isDestroying: boolean;
    readonly isDestroyed: boolean;
    /**
     * Called before the component has been removed from the DOM.
     */
    willDestroy(): void;
}
export {};
