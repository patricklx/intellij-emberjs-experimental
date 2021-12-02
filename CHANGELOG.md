

Changelog
===============================================================================
## 2021.3.0
- upgrade intellij version

## 2021.2.1
- upgrade to latest intellij idea

## v2020.3.29
- fix: correctly resolve internal ember components

## v2020.3.28
- fix: correctly resolve paths from args and fix completion

## v2020.3.27
- fix: correctly resolve paths in hash
- feature: add parameter autocompletion for `{{component x param=1 ...}}`

## v2020.3.26
- feature: add support for args autocompletion in templates (`@xxx`) and reference to own args

## v2020.3.25
- upgrade intellij idea 

## v2020.3.24
- fix: correctly show literal types, (e.g. number)

## v2020.3.23
- fix: fix tag properties lookup for some cases

## v2020.3.22
- fix: better resolve js types

## v2020.3.21
- feature: resolve imports to templates

## v2020.3.20
- feature: provide auto complete and references for namespaced tags

## v2020.3.19
- fix: allow dynamic values for literal tag attributes

## v2020.3.18
- feature: show from which package comes helper/component/modifier

## v2020.3.17
- fix: attributes also allow non-literal if not all are literal

## v2020.3.16
- feature: support .gjs files
- fix: attributes autocomplete only for literal types

## v2020.3.15
- fix: add tag variants from imports

## v2020.3.14
- fix: template-imports is now hbs imports

## v2020.3.13
- fix: resolve common tags normally

## v2020.3.12
- fix: improve tag attribute values resolve
- fix: improve imports lookup

## v2020.3.11
- feature: resolve `or` helper
- fix: import path completion
- resolve in addon imports

## v2020.3.10
- feature: follow components invoked with {{component x}}

## v2020.3.9
- fix: correctly process multi import statements

## v2020.3.8
- fix: node_modules lookup

## v2020.3.7
- fix: references and completions from yields
- fix: rename tag & param works again
- improve: references and completions from yields by param

## v2020.3.6
- fix: clean cache for attr descriptors between versions

## v2020.3.5
- fix: resolve reference to project ts/d.ts files correctly

## v2020.3.4
- update intellij to 2020.3

## v2020.3.3
- fix: resolve components with custom name (not component.ts) correctly

## v2020.3.2
- fix: resolve export references correctly

## v2020.3.1
- Fix issue with lookup of scoped packages
- Fix issue with lookup of modifier

## v2020.3.0
- Handlebars references for tags/mustache paths and tag attributes
- Handlebars autocompletion for tags and mustache paths
- Handlebars parameter hints for helpers/modifiers and components
- Handlebars renaming for mustache ids and html tags

## v2020.2.3

- Fix off-by-one issue in the ember-template-lint annotator (#327)


## v2020.2.2

- Add support for CSS modules in "Goto Related" dialog (#321)


## v2020.2.1

- Change IntelliJ target version to v2020.2 (#320)


## v2020.1.2

- Add support for component template colocation (#316)


## v2020.1.1

- Change IntelliJ target version to v2020.1 (#310)


## v2019.3.5

- Ignore `ember-template-lint` parsing errors (#301)


## v2019.3.4

- Add `ember-template-lint` integration (#296)


## v2019.3.3

- Fix "Attribute not allowed here" warnings (#295)


## v2019.3.2

- Angle bracket autocomplete and goto references (#293)
- Fix path to `ember-cli` executable (#291)


## v2019.3.1

- Change IntelliJ target version to v2019.3 (#286)


## v2019.2.1

- Change IntelliJ target version to v2019.2 EAP (#268)
- Expose field to configure run configuration environment (#257)


## v2019.1.2

- Allow users to configure node interpreter (#254)


## v2019.1.1

- Change IntelliJ target version to v2019.1 (#251)


## v2018.3.2

- Fix random StringIndexOutOfBounds exceptions (#244)


## v2018.3.1

- Change IntelliJ target version to v2018.3 (#232)


## v2018.2.4

- Fix class navigation (#220)
- Add option to specify a custom module to run the run configuration in (#218)


## v2018.2.3

- Fix `NoClassDefFoundError: org/apache/commons/lang3/SystemUtils` error (#217)


## v2018.2.2

- Add support for ember test run configuration (#166)
- Run `ember` binary directly (#210)


## v2018.2.1

- Change IntelliJ target version to v2018.2 (#206)


## v2018.1.2

- Fix "NullPointerException" (#193)


## v2018.1.1

- Add TypeScript support (#175)
- Use node interpreter path from the project settings (#178)
- Change IntelliJ target version to v2018.1 (#185)


## v2017.3.2

- Add "Ember Serve" run configuration (#165)


## v2017.3.1

- Change IntelliJ target version to v2017.3
- Resolve absolute imports for addons (#140)
- Annotate model relationships (#148)
- Add "addon-test-support" directory to reference search path (#150)
- Add temporary ember-try folders as excluded folders (#157)


## v2017.2.1

- Change IntelliJ target version to v2017.2


## v2017.1.3

- Allow navigation to modules in `addon` and `test/dummy/app` folders (#139)


## v2017.1.2

- Merge changes from latest v2016.3 releases


## v2017.1.1

- Change IntelliJ target version to v2017.1


## v2016.3.5

- Fix project detections for WebStorm, PHPStorm, RubyMine, PyCharm, etc. (#127)


## v2016.3.4

- Use slash instead of dot for nested component autocomplete (#119)


## v2016.3.3

- Add support for ESLint and improve JSHint support (#116)


## v2016.3.2

- Resolve absolute imports from your own project (#103)
- Add Ember CLI project generators (#105)


## v2016.3.1

- Change IntelliJ target version to v2016.3


## v2016.2.6

- Fix "Unknown Module Type" warnings (#102)


## v2016.2.5

- Replace "Ember.js" module with framework detector (#100)


## v2016.2.4

- Add [ember-i18n](https://github.com/jamesarosen/ember-i18n) support (#96)


## v2016.2.3

- ember-intl: Add support for JSON translation files (#95)
- ember-intl: Use `baseLocale` property (#93)


## v2016.2.2

- Add [ember-intl](https://github.com/jasonmit/ember-intl) support (#90)


## v2016.2.1

- Change release naming scheme to reflect IntelliJ target version
- Add `{{link-to}}` target completion and resolving to Handlebars 
  templates (#71)
- Add `readonly` helper completion to Handlebars templates (#81)


## v2.1.0

- Add component and helper name completion and resolving to Handlebars
  templates (#62 and #65)


## v2.0.0

- Update minimum IntelliJ version to 2016.1
- Compile with JDK 1.8
- Add `.handlebars` file extension support (#55)
- Add `test`, `pauseTest` and `andThen` live templates (#56 and #57)
- Add support for in-repo-addons (#54)
- Add support for acceptance tests (#58)


## v1.5.2

- Fix still broken `ember` invocation (#50)


## v1.5.1

- Fix broken `ember` invocation


## v1.5.0

- Add `Exclude node_modules/bower_components folder` application settings
- Use "Resource Folders" only for IntelliJ
- Update Kotlin to v1.0.1-2


## v1.4.2

- Fix `java.lang.IllegalStateException: it.virtualFile must not be null` (#37)


## v1.4.1

- Revert automated build process to use JDK 1.7 again


## v1.4.0

- Add IntelliJ 14 (all `141.*` builds) compatibility
- Add IntelliJ 16 (all `144.*` builds and above) compatibility
- Only enable JSHint automatically if `.jshintrc` file was found
- Add basic support for Ember.js addons
- Use locally installed `ember` executable


## v1.3.1

- Fix Ember.js project detection in PyCharm, WebStorm, RubyMine, ...
- Fix Ember.js project detection if subfolders contain Java files
- Fix ember-cli exception and broken timeout
- Add icons for Darcula UI theme


## v1.3.0

- `Navigate → Related Symbol...` for tests
- Show file type icons in project tree view
- more Live Templates for HTMLBars
- Basic reference resolving and completion for e.g. `DS.belongsTo('user')`

![Reference Resolving](doc/references.png)

![Completion](doc/completion.png)

- Generate Ember.js files via `ember generate`

![Blueprints Dialog](doc/blueprints-dialog.png)



## v1.2.0

- Enable JSHint using `.jshintrc`
- Marks `node_modules` and `bower_components` as library folders
- Quick navigation via `Navigate → Related Symbol...` for all major app components
- Live templates

![Live Templates](doc/live-templates.png)


## v1.1.1

- Fix missing icon file


## v1.1.0

- Quick navigation via `Navigate → Class...` for all major app components

![Navigate → Class...](doc/goto-class.png)


## v1.0.2

- Adjust plugin description


## v1.0.1

- Adjust plugin vendor information


## v1.0.0

- Ember.js project discovery (via `app/app.js`) when imported from existing sources
- Automatically sets the language level to ES6
- Marks `app`, `public` and `tests` folders as special folders
