plugins {
    kotlin("js") version "1.6.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-js")
    implementation("com.ionspin.kotlin:bignum:0.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")
    implementation(npm("html-webpack-plugin", "5.0.0-beta.5"))
    implementation(npm("react-dev-utils", "12.0.0"))
}

kotlin {
    js {
        browser {
            webpackTask {
                cssSupport.enabled = true
                output.libraryTarget = "commonjs2"
                sourceMaps = false
            }


            runTask {
                cssSupport.enabled = true
            }

            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
        binaries.executable()
    }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "16.0.0"
}

tasks.register<Copy>("copyProductionFile") {
    dependsOn("browserProductionWebpack")
    from("$buildDir/distributions/index.html")
    into("$projectDir")
    filter { line -> line.replace("module.exports.salary=r", "") }
}
