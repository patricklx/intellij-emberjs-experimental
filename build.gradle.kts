import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij.platform") version "2.18.1"
//    id("org.jetbrains.intellij.platform.migration") version "2.0.0-beta7"
}


group = "com.emberjs"
version = "2025.3.1"

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")
    implementation(kotlin("test"))
    implementation("org.codehaus.jettison:jettison:1.5.7")

    // see https://www.jetbrains.com/intellij-repository/releases/
    // and https://www.jetbrains.com/intellij-repository/snapshots/
    // https://plugins.jetbrains.com/plugin/6884-handlebars-mustache/versions/stable
    intellijPlatform {
        bundledPlugins(listOf("com.dmarcotte.handlebars", "JavaScript", "com.intellij.css", "org.jetbrains.plugins.yaml", "com.intellij.modules.json", "intellij.javascript.eslint"))
        bundledModules(listOf("intellij.platform.lsp.impl", "intellij.platform.structureView", "intellij.xml.structureView", "intellij.xml.structureView.impl", "intellij.platform.smRunner", "intellij.platform.testRunner"))
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        create(IntelliJPlatformType.WebStorm, "2026.2.3")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Configure gradle-intellij-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-intellij-plugin
intellijPlatform {
    pluginConfiguration {
        name.set("EmberExperimental.js")
        ideaVersion {
            // The JavaScript plugin APIs we extend (BaseLspTypeScriptService, ESLint, LSP impl) are internal and
            // change incompatibly between releases - even between 262 patch releases (2026.2 -> 2026.2.3 turned
            // TypeScriptService.getSignatureHelp into a suspend function). Only claim compatibility with the
            // builds we compile and verify against, so a new IDE release can't silently load a broken build.
            sinceBuild.set("262.10968")
            untilBuild.set("262.*")
        }
    }

    pluginVerification {
        // The verifier does not resolve content modules that live inside bundled plugins (the Test Runner and
        // Structure View plugins in 2026.2), so it reports their classes as missing even though they are declared
        // in plugin.xml <dependencies> and load fine at runtime.
        ignoredProblemsFile.set(file("gradle/plugin-verifier-ignored-problems.txt"))
        // Internal API usage is inherent to the Glint/ESLint integrations, so only fail on real binary problems.
        failureLevel.set(listOf(VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS, VerifyPluginTask.FailureLevel.INVALID_PLUGIN))
        ides {
            create(IntelliJPlatformType.WebStorm, "2026.2.3")
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2026.2.3")
        }
    }

    // Plugin Dependencies -> https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html
    // Example: platformPlugins = com.intellij.java, com.jetbrains.php:203.4449.22
    //
    // com.dmarcotte.handlebars: see https://plugins.jetbrains.com/plugin/6884-handlebars-mustache/versions
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    compileTestKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    publishPlugin {
        token.set(System.getenv("ORG_GRADLE_PROJECT_intellijPublishToken"))
    }

}


tasks.buildSearchableOptions {
    enabled = false
}


tasks.register("printVersion") {
    doLast { println(version) }
}


tasks.register("updateChangelog") {
    doLast {
        var input = generateSequence(::readLine).joinToString("\n")
        input = input.replace(":rocket:", "🚀")
        input = input.replace(":bug:", "🐛")
        input = input.replace(":documentation:", "📝")
        input = input.replace(":breaking:", "💥")
        input += "\nsee <a href=\"https://github.com/patricklx/intellij-emberjs-experimental/blob/main/CHANGELOG.md\">https://github.com/patricklx/intellij-emberjs-experimental/</a> for more"
        val f = File("./src/main/resources/META-INF/plugin.xml")
        var content = f.readText()
        content = content.replace("CHANGELOG_PLACEHOLDER", input)
        f.writeText(content)
    }
}

tasks.register("listRecentReleased") {
    doLast {
        val text = URI("https://plugins.jetbrains.com/api/plugins/15499/updates?channel=&size=8").toURL().readText()
        val obj = groovy.json.JsonSlurper().parseText(text)
        val versions = (obj as ArrayList<Map<*,*>>).map { it.get("version") }
        println(groovy.json.JsonBuilder(versions).toPrettyString())
    }
}

tasks.register("verifyAlreadyReleased") {
    doLast {
        var input = generateSequence(::readLine).joinToString("\n")
        val text = URI("https://plugins.jetbrains.com/api/plugins/15499/updates?channel=&size=100").toURL().readText()
        val obj = groovy.json.JsonSlurper().parseText(text)
        val versions = (obj as ArrayList<Map<*,*>>).map { it.get("version") }
        println(versions.contains(input))
    }
}
