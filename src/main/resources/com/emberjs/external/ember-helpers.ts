class Component {}
class Helper {}
class Modifier {}

/**
 The `{{#each}}` helper loops over elements in a collection. It is an extension
 of the base Handlebars `{{#each}}` helper.
 The default behavior of `{{#each}}` is to yield its inner block once for every
 item in an array passing the item as the first block parameter.
 Assuming the `@developers` argument contains this array:
 ```javascript
 [{ name: 'Yehuda' },{ name: 'Tom' }, { name: 'Paul' }];
 ```
 ```handlebars
 <ul>
 {{#each @developers as |person|}}
 <li>Hello, {{person.name}}!</li>
 {{/each}}
 </ul>
 ```
 The same rules apply to arrays of primitives.
 ```javascript
 ['Yehuda', 'Tom', 'Paul']
 ```
 ```handlebars
 <ul>
 {{#each @developerNames as |name|}}
 <li>Hello, {{name}}!</li>
 {{/each}}
 </ul>
 ```
 During iteration, the index of each item in the array is provided as a second block
 parameter.
 ```handlebars
 <ul>
 {{#each @developers as |person index|}}
 <li>Hello, {{person.name}}! You're number {{index}} in line</li>
 {{/each}}
 </ul>
 ```
 ### Specifying Keys
 In order to improve rendering speed, Ember will try to reuse the DOM elements
 where possible. Specifically, if the same item is present in the array both
 before and after the change, its DOM output will be reused.
 The `key` option is used to tell Ember how to determine if the items in the
 array being iterated over with `{{#each}}` has changed between renders. By
 default the item's object identity is used.
 This is usually sufficient, so in most cases, the `key` option is simply not
 needed. However, in some rare cases, the objects' identities may change even
 though they represent the same underlying data.
 For example:
 ```javascript
 people.map(person => {
    return { ...person, type: 'developer' };
  });
 ```
 In this case, each time the `people` array is `map`-ed over, it will produce
 an new array with completely different objects between renders. In these cases,
 you can help Ember determine how these objects related to each other with the
 `key` option:
 ```handlebars
 <ul>
 {{#each @developers key="name" as |person|}}
 <li>Hello, {{person.name}}!</li>
 {{/each}}
 </ul>
 ```
 By doing so, Ember will use the value of the property specified (`person.name`
 in the example) to find a "match" from the previous render. That is, if Ember
 has previously seen an object from the `@developers` array with a matching
 name, its DOM elements will be re-used.
 There are two special values for `key`:
 * `@index` - The index of the item in the array.
 * `@identity` - The item in the array itself.
 ### {{else}} condition
 `{{#each}}` can have a matching `{{else}}`. The contents of this block will render
 if the collection is empty.
 ```handlebars
 <ul>
 {{#each @developers as |person|}}
 <li>{{person.name}} is available!</li>
 {{else}}
 <li>Sorry, nobody is available for this task.</li>
 {{/each}}
 </ul>
 ```
 @method each
 @for Ember.Templates.helpers
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/each?anchor=each
 @public
 */
function each<T>(params: [items: T[]], hash: {key: keyof T|'@index'|'@identity'}) {}

/**
 The `let` helper receives one or more positional arguments and yields
 them out as block params.
 This allows the developer to introduce shorter names for certain computations
 in the template.
 This is especially useful if you are passing properties to a component
 that receives a lot of options and you want to clean up the invocation.
 For the following example, the template receives a `post` object with
 `content` and `title` properties.
 We are going to call the `my-post` component, passing a title which is
 the title of the post suffixed with the name of the blog, the content
 of the post, and a series of options defined in-place.
 ```handlebars
 {{#let
        (concat post.title ' | The Ember.js Blog')
        post.content
        (hash
          theme="high-contrast"
          enableComments=true
        )
        as |title content options|
    }}
 <MyPost @title={{title}} @content={{content}} @options={{options}} />
 {{/let}}
 ```
 or
 ```handlebars
 {{#let
        (concat post.title ' | The Ember.js Blog')
        post.content
        (hash
          theme="high-contrast"
          enableComments=true
        )
        as |title content options|
    }}
 {{my-post title=title content=content options=options}}
 {{/let}}
 ```
 @method let
 @for Ember.Templates.helpers
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/let?anchor=let
 @public
 */
function _let(params: [value: any]) {}

/**
 The `fn` helper allows you to ensure a function that you are passing off
 to another component, helper, or modifier has access to arguments that are
 available in the template.
 For example, if you have an `each` helper looping over a number of items, you
 may need to pass a function that expects to receive the item as an argument
 to a component invoked within the loop. Here's how you could use the `fn`
 helper to pass both the function and its arguments together:
 ```app/templates/components/items-listing.hbs
 {{#each @items as |item|}}
 <DisplayItem @item=item @select={{fn this.handleSelected item}} />
 {{/each}}
 ```
 ```app/components/items-list.js
 import Component from '@glimmer/component';
 import { action } from '@ember/object';
 export default class ItemsList extends Component {
    @action
    handleSelected(item) {
      // ...snip...
    }
  }
 ```
 In this case the `display-item` component will receive a normal function
 that it can invoke. When it invokes the function, the `handleSelected`
 function will receive the `item` and any arguments passed, thanks to the
 `fn` helper.
 Let's take look at what that means in a couple circumstances:
 - When invoked as `this.args.select()` the `handleSelected` function will
 receive the `item` from the loop as its first and only argument.
 - When invoked as `this.args.select('foo')` the `handleSelected` function
 will receive the `item` from the loop as its first argument and the
 string `'foo'` as its second argument.
 In the example above, we used `@action` to ensure that `handleSelected` is
 properly bound to the `items-list`, but let's explore what happens if we
 left out `@action`:
 ```app/components/items-list.js
 import Component from '@glimmer/component';
 export default class ItemsList extends Component {
    handleSelected(item) {
      // ...snip...
    }
  }
 ```
 In this example, when `handleSelected` is invoked inside the `display-item`
 component, it will **not** have access to the component instance. In other
 words, it will have no `this` context, so please make sure your functions
 are bound (via `@action` or other means) before passing into `fn`!
 See also [partial application](https://en.wikipedia.org/wiki/Partial_application).
 @method fn
 @for Ember.Templates.helpers
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/fn?anchor=fn
 @public
 @since 3.11.0
 */
declare function fn<R,X extends (...args: any) => R >(params: [action: X, ...args: any[]]): R

/**
 Use the `{{array}}` helper to create an array to pass as an option to your
 components.
 ```handlebars
 <MyComponent @people={{array
     'Tom Dale'
     'Yehuda Katz'
     this.myOtherPerson}}
 />
 ```
 or
 ```handlebars
 {{my-component people=(array
     'Tom Dale'
     'Yehuda Katz'
     this.myOtherPerson)
   }}
 ```
 Would result in an object such as:
 ```js
 ['Tom Dale', 'Yehuda Katz', this.get('myOtherPerson')]
 ```
 Where the 3rd item in the array is bound to updates of the `myOtherPerson` property.
 @method array
 @for Ember.Templates.helpers
 @param {Array} options
 @return {Array} Array
 @since 3.8.0
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/array?anchor=array
 @public
 */
declare function array<T>(options: T[]): T[];

/**
 The `{{component}}` helper lets you add instances of `Component` to a
 template. See [Component](/ember/release/classes/Component) for
 additional information on how a `Component` functions.
 `{{component}}`'s primary use is for cases where you want to dynamically
 change which type of component is rendered as the state of your application
 changes. This helper has three modes: inline, block, and nested.
 ### Inline Form
 Given the following template:
 ```app/application.hbs
 {{component this.infographicComponentName}}
 ```
 And the following application code:
 ```app/controllers/application.js
 import Controller from '@ember/controller';
 import { tracked } from '@glimmer/tracking';
 export default class ApplicationController extends Controller {
    @tracked isMarketOpen = 'live-updating-chart'
    get infographicComponentName() {
      return this.isMarketOpen ? 'live-updating-chart' : 'market-close-summary';
    }
  }
 ```
 The `live-updating-chart` component will be appended when `isMarketOpen` is
 `true`, and the `market-close-summary` component will be appended when
 `isMarketOpen` is `false`. If the value changes while the app is running,
 the component will be automatically swapped out accordingly.
 Note: You should not use this helper when you are consistently rendering the same
 component. In that case, use standard component syntax, for example:
 ```app/templates/application.hbs
 <LiveUpdatingChart />
 ```
 or
 ```app/templates/application.hbs
 {{live-updating-chart}}
 ```
 ### Block Form
 Using the block form of this helper is similar to using the block form
 of a component. Given the following application template:
 ```app/templates/application.hbs
 {{#component this.infographicComponentName}}
 Last update: {{this.lastUpdateTimestamp}}
 {{/component}}
 ```
 The following controller code:
 ```app/controllers/application.js
 import Controller from '@ember/controller';
 import { computed } from '@ember/object';
 import { tracked } from '@glimmer/tracking';
 export default class ApplicationController extends Controller {
    @tracked isMarketOpen = 'live-updating-chart'
    get lastUpdateTimestamp() {
      return new Date();
    }
    get infographicComponentName() {
      return this.isMarketOpen ? 'live-updating-chart' : 'market-close-summary';
    }
  }
 ```
 And the following component template:
 ```app/templates/components/live-updating-chart.hbs
 {{! chart }}
 {{yield}}
 ```
 The `Last Update: {{this.lastUpdateTimestamp}}` will be rendered in place of the `{{yield}}`.
 ### Nested Usage
 The `component` helper can be used to package a component path with initial attrs.
 The included attrs can then be merged during the final invocation.
 For example, given a `person-form` component with the following template:
 ```app/templates/components/person-form.hbs
 {{yield (hash
    nameInput=(component "my-input-component" value=@model.name placeholder="First Name")
  )}}
 ```
 When yielding the component via the `hash` helper, the component is invoked directly.
 See the following snippet:
 ```
 <PersonForm as |form|>
 <form.nameInput @placeholder="Username" />
 </PersonForm>
 ```
 or
 ```
 {{#person-form as |form|}}
 {{form.nameInput placeholder="Username"}}
 {{/person-form}}
 ```
 Which outputs an input whose value is already bound to `model.name` and `placeholder`
 is "Username".
 When yielding the component without the `hash` helper use the `component` helper.
 For example, below is a `full-name` component template:
 ```handlebars
 {{yield (component "my-input-component" value=@model.name placeholder="Name")}}
 ```
 ```
 <FullName as |field|>
 {{component field placeholder="Full name"}}
 </FullName>
 ```
 or
 ```
 {{#full-name as |field|}}
 {{component field placeholder="Full name"}}
 {{/full-name}}
 ```
 @method component
 @since 1.11.0
 @for Ember.Templates.helpers
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/component?anchor=component
 @public
 */
function component(args: [component: string|Component, args: any[]], hash: {[x: string]: any}): Component|undefined {
  return
}

/**
 Concatenates the given arguments into a string.
 Example:
 ```handlebars
 {{some-component name=(concat firstName " " lastName)}}
 {{! would pass name="<first name value> <last name value>" to the component}}
 ```
 or for angle bracket invocation, you actually don't need concat at all.
 ```handlebars
 <SomeComponent @name="{{firstName}} {{lastName}}" />
 ```
 @public
 @method concat
 @for Ember.Templates.helpers
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/concat?anchor=concat
 @since 1.13.0
 */
function concat(params: string[]): string {
  return ""
}


/**
 Execute the `debugger` statement in the current template's context.
 ```handlebars
 {{debugger}}
 ```
 When using the debugger helper you will have access to a `get` function. This
 function retrieves values available in the context of the template.
 For example, if you're wondering why a value `{{foo}}` isn't rendering as
 expected within a template, you could place a `{{debugger}}` statement and,
 when the `debugger;` breakpoint is hit, you can attempt to retrieve this value:
 ```
 > get('foo')
 ```
 `get` is also aware of keywords. So in this situation
 ```handlebars
 {{#each this.items as |item|}}
 {{debugger}}
 {{/each}}
 ```
 You'll be able to get values from the current item:
 ```
 > get('item.name')
 ```
 You can also access the context of the view to make sure it is the object that
 you expect:
 ```
 > context
 ```
 @method debugger
 @for Ember.Templates.helpers
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/debugger?anchor=debugger
 @public
 */
function _debugger(params: string[]) {}

/**
 The `{{each-in}}` helper loops over properties on an object.
 For example, given this component definition:
 ```app/components/developer-details.js
 import Component from '@glimmer/component';
 import { tracked } from '@glimmer/tracking';
 export default class extends Component {
    @tracked developer = {
      "name": "Shelly Sails",
      "age": 42
    };
  }
 ```
 This template would display all properties on the `developer`
 object in a list:
 ```app/components/developer-details.hbs
 <ul>
 {{#each-in this.developer as |key value|}}
 <li>{{key}}: {{value}}</li>
 {{/each-in}}
 </ul>
 ```
 Outputting their name and age.
 @method each-in
 @for Ember.Templates.helpers
 @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/each-in?anchor=each-in
 @public
 @since 2.1.0
 */
function eachIn(params: [object: Object]) {}

/**
 Dynamically look up a property on an object. The second argument to `{{get}}`
 should have a string value, although it can be bound.
 For example, these two usages are equivalent:
 ```app/components/developer-detail.js
 import Component from '@glimmer/component';
 import { tracked } from '@glimmer/tracking';
 export default class extends Component {
    @tracked developer = {
      name: "Sandi Metz",
      language: "Ruby"
    }
  }
 ```
 ```handlebars
 {{this.developer.name}}
 {{get this.developer "name"}}
 ```
 If there were several facts about a person, the `{{get}}` helper can dynamically
 pick one:
 ```app/templates/application.hbs
 <DeveloperDetail @factName="language" />
 ```
 ```handlebars
 {{get this.developer @factName}}
 ```
 For a more complex example, this template would allow the user to switch
 between showing the user's height and weight with a click:
 ```app/components/developer-detail.js
 import Component from '@glimmer/component';
 import { tracked } from '@glimmer/tracking';
 export default class extends Component {
    @tracked developer = {
      name: "Sandi Metz",
      language: "Ruby"
    }
    @tracked currentFact = 'name'
    @action
    showFact(fact) {
      this.currentFact = fact;
    }
  }
 ```
 ```app/components/developer-detail.js
 {{get this.developer this.currentFact}}
 <button {{on 'click' (fn this.showFact "name")}}>Show name</button>
 <button {{on 'click' (fn this.showFact "language")}}>Show language</button>
 ```
 The `{{get}}` helper can also respect mutable values itself. For example:
 ```app/components/developer-detail.js
 <Input @value={{mut (get this.person this.currentFact)}} />
 <button {{on 'click' (fn this.showFact "name")}}>Show name</button>
 <button {{on 'click' (fn this.showFact "language")}}>Show language</button>
 ```
 Would allow the user to swap what fact is being displayed, and also edit
 that fact via a two-way mutable binding.
 @public
 @method get
 @for Ember.Templates.helpers
 @since 2.1.0
 */
function get(params: [object: Object, path: string]): any {}

/**
 The `if` helper allows you to conditionally render one of two branches,
 depending on the "truthiness" of a property.
 For example the following values are all falsey: `false`, `undefined`, `null`, `""`, `0`, `NaN` or an empty array.
 This helper has two forms, block and inline.
 ## Block form
 You can use the block form of `if` to conditionally render a section of the template.
 To use it, pass the conditional value to the `if` helper,
 using the block form to wrap the section of template you want to conditionally render.
 Like so:
 ```app/templates/application.hbs
 <Weather />
 ```
 ```app/components/weather.hbs
 {{! will not render because greeting is undefined}}
 {{#if @isRaining}}
 Yes, grab an umbrella!
 {{/if}}
 ```
 You can also define what to show if the property is falsey by using
 the `else` helper.
 ```app/components/weather.hbs
 {{#if @isRaining}}
 Yes, grab an umbrella!
 {{else}}
 No, it's lovely outside!
 {{/if}}
 ```
 You are also able to combine `else` and `if` helpers to create more complex
 conditional logic.
 For the following template:
 ```app/components/weather.hbs
 {{#if @isRaining}}
 Yes, grab an umbrella!
 {{else if @isCold}}
 Grab a coat, it's chilly!
 {{else}}
 No, it's lovely outside!
 {{/if}}
 ```
 If you call it by saying `isCold` is true:
 ```app/templates/application.hbs
 <Weather @isCold={{true}} />
 ```
 Then `Grab a coat, it's chilly!` will be rendered.
 ## Inline form
 The inline `if` helper conditionally renders a single property or string.
 In this form, the `if` helper receives three arguments, the conditional value,
 the value to render when truthy, and the value to render when falsey.
 For example, if `useLongGreeting` is truthy, the following:
 ```app/templates/application.hbs
 <Greeting @useLongGreeting={{true}} />
 ```
 ```app/components/greeting.hbs
 {{if @useLongGreeting "Hello" "Hi"}} Alex
 ```
 Will render:
 ```html
 Hello Alex
 ```
 One detail to keep in mind is that both branches of the `if` helper will be evaluated,
 so if you have `{{if condition "foo" (expensive-operation "bar")`,
  `expensive-operation` will always calculate.
  @method if
  @for Ember.Templates.helpers
  @see https://api.emberjs.com/ember/release/classes/Ember.Templates.helpers/methods/if?anchor=if
  @public
 */
function _if(params: [condition: boolean, then: any, otherwise: any]): any {}


/**
 The `in-element` helper renders its block content outside of the regular flow,
 into a DOM element given by its `destinationElement` positional argument.
 Common use cases - often referred to as "portals" or "wormholes" - are rendering
 dropdowns, modals or tooltips close to the root of the page to bypass CSS overflow
 rules, or to render content to parts of the page that are outside of the control
 of the Ember app itself (e.g. embedded into a static or server rendered HTML page).
 ```handlebars
 {{#in-element this.destinationElement}}
 <div>Some content</div>
 {{/in-element}}
 ```
 ### Arguments
 `{{in-element}}` requires a single positional argument:
 - `destinationElement` -- the DOM element to render into. It must exist at the time
 of rendering.
 It also supports an optional named argument:
 - `insertBefore` -- by default the DOM element's content is replaced when used as
 `destinationElement`. Passing `null` changes the behaviour to appended at the end
 of any existing content. Any other value than `null` is currently not supported.
 @method in-element
 @for Ember.Templates.helpers
 @public
 */
function in_element(params: [destinationElement: Element], hash?: {insertBefore: null}): void {}


/**
 The `unless` helper is the inverse of the `if` helper. It displays if a value
 is falsey ("not true" or "is false"). Example values that will display with
 `unless`: `false`, `undefined`, `null`, `""`, `0`, `NaN` or an empty array.
 ## Inline form
 The inline `unless` helper conditionally renders a single property or string.
 This helper acts like a ternary operator. If the first property is falsy,
 the second argument will be displayed, otherwise, the third argument will be
 displayed
 For example, if you pass a falsey `useLongGreeting` to the `Greeting` component:
 ```app/templates/application.hbs
 <Greeting @useLongGreeting={{false}} />
 ```
 ```app/components/greeting.hbs
 {{unless @useLongGreeting "Hi" "Hello"}} Ben
 ```
 Then it will display:
 ```html
 Hi Ben
 ```
 ## Block form
 Like the `if` helper, the `unless` helper also has a block form.
 The following will not render anything:
 ```app/templates/application.hbs
 <Greeting />
 ```
 ```app/components/greeting.hbs
 {{#unless @greeting}}
 No greeting was found. Why not set one?
 {{/unless}}
 ```
 You can also use an `else` helper with the `unless` block. The
 `else` will display if the value is truthy.
 If you have the following component:
 ```app/components/logged-in.hbs
 {{#unless @userData}}
 Please login.
 {{else}}
 Welcome back!
 {{/unless}}
 ```
 Calling it with a truthy `userData`:
 ```app/templates/application.hbs
 <LoggedIn @userData={{hash username="Zoey"}} />
 ```
 Will render:
 ```html
 Welcome back!
 ```
 and calling it with a falsey `userData`:
 ```app/templates/application.hbs
 <LoggedIn @userData={{false}} />
 ```
 Will render:
 ```html
 Please login.
 ```
 @method unless
 @for Ember.Templates.helpers
 @public
 */
declare function unless(params: [condition: boolean, then: any, otherwise: any]): any;

/**
 The `{{outlet}}` helper lets you specify where a child route will render in
 your template. An important use of the `{{outlet}}` helper is in your
 application's `application.hbs` file:
 ```app/templates/application.hbs
 <MyHeader />
 <div class="my-dynamic-content">
 <!-- this content will change based on the current route, which depends on the current URL -->
 {{outlet}}
 </div>
 <MyFooter />
 ```
 See the [routing guide](https://guides.emberjs.com/release/routing/rendering-a-template/) for more
 information on how your `route` interacts with the `{{outlet}}` helper.
 Note: Your content __will not render__ if there isn't an `{{outlet}}` for it.
 @method outlet
 @for Ember.Templates.helpers
 @public
 */
declare function outlet(): any;

/**
 Use the `{{hash}}` helper to create a hash to pass as an option to your
 components. This is specially useful for contextual components where you can
 just yield a hash:
 ```handlebars
 {{yield (hash
      name='Sarah'
      title=office
   )}}
 ```
 Would result in an object such as:
 ```js
 { name: 'Sarah', title: this.get('office') }
 ```
 Where the `title` is bound to updates of the `office` property.
 Note that the hash is an empty object with no prototype chain, therefore
 common methods like `toString` are not available in the resulting hash.
 If you need to use such a method, you can use the `call` or `apply`
 approach:
 ```js
 function toString(obj) {
     return Object.prototype.toString.apply(obj);
   }
 ```
 @method hash
 @for Ember.Templates.helpers
 @param args
 @param {Object} hash
 @return {Object} Hash
 @since 2.3.0
 @public
 */
declare function hash(args: never, hash: {[x: string]: any}): any


/**
 `{{yield}}` denotes an area of a template that will be rendered inside
 of another template.

 ### Use with `Component`

 When designing components `{{yield}}` is used to denote where, inside the component's
 template, an optional block passed to the component should render:

 ```app/templates/application.hbs
 <LabeledTextfield @value={{@model.name}}>
 First name:
 </LabeledTextfield>
 ```

 ```app/components/labeled-textfield.hbs
 <label>
 {{yield}} <Input @value={{@value}} />
 </label>
 ```

 Result:

 ```html
 <label>
 First name: <input type="text" />
 </label>
 ```

 Additionally you can `yield` properties into the context for use by the consumer:

 ```app/templates/application.hbs
 <LabeledTextfield @value={{@model.validation}} @validator={{this.firstNameValidator}} as |validationError|>
 {{#if validationError}}
 <p class="error">{{validationError}}</p>
 {{/if}}
 First name:
 </LabeledTextfield>
 ```

 ```app/components/labeled-textfield.hbs
 <label>
 {{yield this.validationError}} <Input @value={{@value}} />
 </label>
 ```

 Result:

 ```html
 <label>
 <p class="error">First Name must be at least 3 characters long.</p>
 First name: <input type="text" />
 </label>
 ```

 `yield` can also be used with the `hash` helper:

 ```app/templates/application.hbs
 <DateRanges @value={{@model.date}} as |range|>
 Start date: {{range.start}}
 End date: {{range.end}}
 </DateRanges>
 ```

 ```app/components/date-ranges.hbs
 <div>
 {{yield (hash start=@value.start end=@value.end)}}
 </div>
 ```

 Result:

 ```html
 <div>
 Start date: July 1st
 End date: July 30th
 </div>
 ```

 Multiple values can be yielded as block params:

 ```app/templates/application.hbs
 <Banner @value={{@model}} as |title subtitle body|>
 <h1>{{title}}</h1>
 <h2>{{subtitle}}</h2>
 {{body}}
 </Banner>
 ```

 ```app/components/banner.hbs
 <div>
 {{yield "Hello title" "hello subtitle" "body text"}}
 </div>
 ```

 Result:

 ```html
 <div>
 <h1>Hello title</h1>
 <h2>hello subtitle</h2>
 body text
 </div>
 ```

 However, it is preferred to use the hash helper, as this can prevent breaking changes to your component and also simplify the api for the component.

 Multiple components can be yielded with the `hash` and `component` helper:

 ```app/templates/application.hbs
 <Banner @value={{@model}} as |banner|>
 <banner.Title>Banner title</banner.Title>
 <banner.Subtitle>Banner subtitle</banner.Subtitle>
 <banner.Body>A load of body text</banner.Body>
 </Banner>
 ```

 ```app/components/banner.js
 import Title from './banner/title';
 import Subtitle from './banner/subtitle';
 import Body from './banner/body';

 export default class Banner extends Component {
    Title = Title;
    Subtitle = Subtitle;
    Body = Body;
  }
 ```

 ```app/components/banner.hbs
 <div>
 {{yield (hash
      Title=this.Title
      Subtitle=this.Subtitle
      Body=(component this.Body defaultArg="some value")
    )}}
 </div>
 ```

 Result:

 ```html
 <div>
 <h1>Banner title</h1>
 <h2>Banner subtitle</h2>
 A load of body text
 </div>
 ```

 A benefit of using this pattern is that the user of the component can change the order the components are displayed.

 ```app/templates/application.hbs
 <Banner @value={{@model}} as |banner|>
 <banner.Subtitle>Banner subtitle</banner.Subtitle>
 <banner.Title>Banner title</banner.Title>
 <banner.Body>A load of body text</banner.Body>
 </Banner>
 ```

 Result:

 ```html
 <div>
 <h2>Banner subtitle</h2>
 <h1>Banner title</h1>
 A load of body text
 </div>
 ```

 Another benefit to using `yield` with the `hash` and `component` helper
 is you can pass attributes and arguments to these components:

 ```app/templates/application.hbs
 <Banner @value={{@model}} as |banner|>
 <banner.Subtitle class="mb-1">Banner subtitle</banner.Subtitle>
 <banner.Title @variant="loud">Banner title</banner.Title>
 <banner.Body>A load of body text</banner.Body>
 </Banner>
 ```

 ```app/components/banner/subtitle.hbs
 {{!-- note the use of ..attributes --}}
 <h2 ...attributes>
 {{yield}}
 </h2>
 ```

 ```app/components/banner/title.hbs
 {{#if (eq @variant "loud")}}
 <h1 class="loud">{{yield}}</h1>
 {{else}}
 <h1 class="quiet">{{yield}}</h1>
 {{/if}}
 ```

 Result:

 ```html
 <div>
 <h2 class="mb-1">Banner subtitle</h2>
 <h1 class="loud">Banner title</h1>
 A load of body text
 </div>
 ```

 @method yield
 @for Ember.Templates.helpers
 @param {Hash} options
 @return {String} HTML string
 @public
 */
declare function _yield(args: any[], hash: {to: string});

/**
 `{{(has-block)}}` indicates if the component was invoked with a block.

 This component is invoked with a block:

 ```handlebars
 {{#my-component}}
 Hi Jen!
 {{/my-component}}
 ```

 This component is invoked without a block:

 ```handlebars
 {{my-component}}
 ```

 Using angle bracket invocation, this looks like:

 ```html
 <MyComponent>Hi Jen!</MyComponent> {{! with a block}}
 ```

 ```html
 <MyComponent/> {{! without a block}}
 ```

 This is useful when you want to create a component that can optionally take a block
 and then render a default template when it is not invoked with a block.

 ```app/templates/components/my-component.hbs
 {{#if (has-block)}}
 Welcome {{yield}}, we are happy you're here!
 {{else}}
 Hey you! You're great!
 {{/if}}
 ```

 @method has-block
 @for Ember.Templates.helpers
 @param {String} the name of the block. The name (at the moment) is either "main" or "inverse" (though only curly components support inverse)
 @return {Boolean} `true` if the component was invoked with a block
 @public
 */
declare function hasBlock(params: [blockName: string]): boolean;


/**
 `{{(has-block-params)}}` indicates if the component was invoked with block params.

 This component is invoked with block params:

 ```handlebars
 {{#my-component as |favoriteFlavor|}}
 Hi Jen!
 {{/my-component}}
 ```

 This component is invoked without block params:

 ```handlebars
 {{#my-component}}
 Hi Jenn!
 {{/my-component}}
 ```

 With angle bracket syntax, block params look like this:

 ```handlebars
 <MyComponent as |favoriteFlavor|>
 Hi Jen!
 </MyComponent>
 ```

 And without block params:

 ```handlebars
 <MyComponent>
 Hi Jen!
 </MyComponent>
 ```

 This is useful when you want to create a component that can render itself
 differently when it is not invoked with block params.

 ```app/templates/components/my-component.hbs
 {{#if (has-block-params)}}
 Welcome {{yield this.favoriteFlavor}}, we're happy you're here and hope you
 enjoy your favorite ice cream flavor.
 {{else}}
 Welcome {{yield}}, we're happy you're here, but we're unsure what
 flavor ice cream you would enjoy.
 {{/if}}
 ```

 @method has-block-params
 @for Ember.Templates.helpers
 @param {String} the name of the block. The name (at the moment) is either "main" or "inverse" (though only curly components support inverse)
 @return {Boolean} `true` if the component was invoked with block params
 @public
 */
declare function hasBlockParams(params: [blockName: string]);

/**
 `log` allows you to output the value of variables in the current rendering
 context. `log` also accepts primitive types such as strings or numbers.

 ```handlebars
 {{log "myVariable:" myVariable }}
 ```

 @method log
 @for Ember.Templates.helpers
 @param {Array} params
 @public
 */
declare function log(args: any[]);


/**
 The `mut` helper lets you __clearly specify__ that a child `Component` can update the
 (mutable) value passed to it, which will __change the value of the parent component__.

 To specify that a parameter is mutable, when invoking the child `Component`:

 ```handlebars
 <MyChild @childClickCount={{fn (mut totalClicks)}} />
 ```

 or

 ```handlebars
 {{my-child childClickCount=(mut totalClicks)}}
 ```

 The child `Component` can then modify the parent's value just by modifying its own
 property:

 ```javascript
 // my-child.js
 export default Component.extend({
 click() {
 this.incrementProperty('childClickCount');
 }
 });
 ```

 Note that for curly components (`{{my-component}}`) the bindings are already mutable,
 making the `mut` unnecessary.

 Additionally, the `mut` helper can be combined with the `fn` helper to
 mutate a value. For example:

 ```handlebars
 <MyChild @childClickCount={{this.totalClicks}} @click-count-change={{fn (mut totalClicks))}} />
 ```

 or

 ```handlebars
 {{my-child childClickCount=totalClicks click-count-change=(fn (mut totalClicks))}}
 ```

 The child `Component` would invoke the function with the new click value:

 ```javascript
 // my-child.js
 export default Component.extend({
 click() {
 this.get('click-count-change')(this.get('childClickCount') + 1);
 }
 });
 ```

 The `mut` helper changes the `totalClicks` value to what was provided as the `fn` argument.

 The `mut` helper, when used with `fn`, will return a function that
 sets the value passed to `mut` to its first argument. As an example, we can create a
 button that increments a value passing the value directly to the `fn`:

 ```handlebars
 {{! inc helper is not provided by Ember }}
 <button onclick={{fn (mut count) (inc count)}}>
 Increment count
 </button>
 ```

 @method mut
 @param {Object} [attr] the "two-way" attribute that can be modified.
 @for Ember.Templates.helpers
 @public
 */
declare function mut(params: [attr: any]);


/**
 Use the `{{modifier}}` helper to create contextual modifier so
 that it can be passed around as first-class values in templates.

 ```handlebars
 {{#let (modifier "click-outside" click=this.submit) as |on-click-outside|}}

 {{!-- this is equivalent to `<MyComponent {{click-outside click=this.submit}} />` --}}
 <MyComponent {{on-click-outside}} />

 {{!-- this will pass the modifier itself into the component, instead of invoking it now --}}
 <MyComponent @modifier={{modifier on-click-outside "extra" "args"}} />

 {{!-- this will yield the modifier itself ("contextual modifier"), instead of invoking it now --}}
 {{yield on-click-outside}}
 {{/let}}
 ```

 ### Arguments

 The `{{modifier}}` helper works similarly to the [`{{component}}`](./component?anchor=component) and
 [`{{helper}}`](./helper?anchor=helper) helper:

 * When passed a string (e.g. `(modifier "foo")`) as the first argument,
 it will produce an opaque, internal "modifier definition" object
 that can be passed around and invoked elsewhere.

 * Any additional positional and/or named arguments (a.k.a. params and hash)
 will be stored ("curried") inside the definition object, such that, when invoked,
 these arguments will be passed along to the referenced modifier.

 @method modifier
 @for Ember.Templates.helpers
 @public
 @since 3.27.0
 */
declare function modifier(args: [modifier: string|Modifier, args: any[]], hash: {[x: string]: any}): Modifier|undefined;

/**
 Use the `{{helper}}` helper to create contextual helper so
 that it can be passed around as first-class values in templates.

 ```handlebars
 {{#let (helper "join-words" "foo" "bar" separator=" ") as |foo-bar|}}

 {{!-- this is equivalent to invoking `{{join-words "foo" "bar" separator=" "}}` --}}
 {{foo-bar}}

 {{!-- this will pass the helper itself into the component, instead of invoking it now --}}
 <MyComponent @helper={{helper foo-bar "baz"}} />

 {{!-- this will yield the helper itself ("contextual helper"), instead of invoking it now --}}
 {{yield foo-bar}}
 {{/let}}
 ```

 ### Arguments

 The `{{helper}}` helper works similarly to the [`{{component}}`](./component?anchor=component) and
 [`{{modifier}}`](./modifier?anchor=modifier) helper:

 * When passed a string (e.g. `(helper "foo")`) as the first argument,
 it will produce an opaque, internal "helper definition" object
 that can be passed around and invoked elsewhere.

 * Any additional positional and/or named arguments (a.k.a. params and hash)
 will be stored ("curried") inside the definition object, such that, when invoked,
 these arguments will be passed along to the referenced helper.


 @method helper
 @for Ember.Templates.helpers
 @public
 @since 3.27.0
 */
declare function helper(args: [helper: string|Helper, args: any[]], hash: {[x: string]: any}): Helper|undefined;


/**
 `page-title` allows you to set the title of any page in your application and
 append additional titles for each route. For complete documentation, see
 https://github.com/ember-cli/ember-page-title.

 ```handlebars
 {{page-title "My Page Title" }}
 ```

 @method page-title
 @for Ember.Templates.helpers
 @param {String} param
 @public
 */
declare function pageTitle(args: [title: string]);

/**
 The `{{mount}}` helper lets you embed a routeless engine in a template.
 Mounting an engine will cause an instance to be booted and its `application`
 template to be rendered.

 For example, the following template mounts the `ember-chat` engine:

 ```handlebars
 {{! application.hbs }}
 {{mount "ember-chat"}}
 ```

 Additionally, you can also pass in a `model` argument that will be
 set as the engines model. This can be an existing object:

 ```
 <div>
 {{mount 'admin' model=userSettings}}
 </div>
 ```

 Or an inline `hash`, and you can even pass components:

 ```
 <div>
 <h1>Application template!</h1>
 {{mount 'admin' model=(hash
 title='Secret Admin'
 signInButton=(component 'sign-in-button')
 )}}
 </div>
 ```

 @method mount
 @param {String} name Name of the engine to mount.
 @param {Object} [model] Object that will be set as
  the model of the engine.
 @for Ember.Templates.helpers
 @public
 */
declare function mount(args: [name: string], model: any);


/**
 The `{{unbound}}` helper disconnects the one-way binding of a property,
 essentially freezing its value at the moment of rendering. For example,
 in this example the display of the variable `name` will not change even
 if it is set with a new value:

 ```handlebars
 {{unbound this.name}}
 ```

 Like any helper, the `unbound` helper can accept a nested helper expression.
 This allows for custom helpers to be rendered unbound:

 ```handlebars
 {{unbound (some-custom-helper)}}
 {{unbound (capitalize this.name)}}
 {{! You can use any helper, including unbound, in a nested expression }}
 {{capitalize (unbound this.name)}}
 ```

 The `unbound` helper only accepts a single argument, and it return an
 unbound value.

 @method unbound
 @for Ember.Templates.helpers
 @public
 */
declare function unbound(args: [ref: any]);



/**
 The `readonly` helper let's you specify that a binding is one-way only,
 instead of two-way.
 When you pass a `readonly` binding from an outer context (e.g. parent component),
 to to an inner context (e.g. child component), you are saying that changing that
 property in the inner context does not change the value in the outer context.

 To specify that a binding is read-only, when invoking the child `Component`:

 ```app/components/my-parent.js
 export default Component.extend({
 totalClicks: 3
 });
 ```

 ```app/templates/components/my-parent.hbs
 {{log totalClicks}} // -> 3
 <MyChild @childClickCount={{readonly totalClicks}} />
 ```
 ```
 {{my-child childClickCount=(readonly totalClicks)}}
 ```

 Now, when you update `childClickCount`:

 ```app/components/my-child.js
 export default Component.extend({
 click() {
 this.incrementProperty('childClickCount');
 }
 });
 ```

 The value updates in the child component, but not the parent component:

 ```app/templates/components/my-child.hbs
 {{log childClickCount}} //-> 4
 ```

 ```app/templates/components/my-parent.hbs
 {{log totalClicks}} //-> 3
 <MyChild @childClickCount={{readonly totalClicks}} />
 ```
 or
 ```app/templates/components/my-parent.hbs
 {{log totalClicks}} //-> 3
 {{my-child childClickCount=(readonly totalClicks)}}
 ```

 ### Objects and Arrays

 When passing a property that is a complex object (e.g. object, array) instead of a primitive object (e.g. number, string),
 only the reference to the object is protected using the readonly helper.
 This means that you can change properties of the object both on the parent component, as well as the child component.
 The `readonly` binding behaves similar to the `const` keyword in JavaScript.

 Let's look at an example:

 First let's set up the parent component:

 ```app/components/my-parent.js
 import Component from '@ember/component';

 export default Component.extend({
 clicks: null,

 init() {
 this._super(...arguments);
 this.set('clicks', { total: 3 });
 }
 });
 ```

 ```app/templates/components/my-parent.hbs
 {{log clicks.total}} //-> 3
 <MyChild @childClicks={{readonly clicks}} />
 ```
 ```app/templates/components/my-parent.hbs
 {{log clicks.total}} //-> 3
 {{my-child childClicks=(readonly clicks)}}
 ```

 Now, if you update the `total` property of `childClicks`:

 ```app/components/my-child.js
 import Component from '@ember/component';

 export default Component.extend({
 click() {
 this.get('clicks').incrementProperty('total');
 }
 });
 ```

 You will see the following happen:

 ```app/templates/components/my-parent.hbs
 {{log clicks.total}} //-> 4
 <MyChild @childClicks={{readonly clicks}} />
 ```
 or
 ```app/templates/components/my-parent.hbs
 {{log clicks.total}} //-> 4
 {{my-child childClicks=(readonly clicks)}}
 ```

 ```app/templates/components/my-child.hbs
 {{log childClicks.total}} //-> 4
 ```

 @method readonly
 @param {Object} [attr] the read-only attribute.
 @for Ember.Templates.helpers
 @private
 */
declare function readonly(args: [attr: any]);

export {
 fn,
 component,
 array,
 concat,
 get,
 hash,
 log,
 mut,
}

const helpers = {
  array,
  component,
 'debugger': _debugger,
  each,
 'each-in': eachIn,
  fn,
 'has-block': hasBlock,
 'has-block-params': hasBlockParams,
  hash,
  helper,
 'if': _if,
 'in-element': in_element,
 'let': _let,
  log,
  modifier,
  mount,
  mut,
 'outlet': outlet,
 'page-title': pageTitle,
  concat,
  get,
  unbound,
  readonly,
  'unless': unless,
  'yield': _yield
}

export default helpers;


