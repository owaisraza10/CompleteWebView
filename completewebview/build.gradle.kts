plugins {
    id("com.android.library")
    id("maven-publish")
    // I deleted the Kotlin plugin line here! Let's see if Gradle still complains.
}

android {
    namespace = "com.owaisraza.completewebview"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.10.0")
}

// Put this at the absolute bottom of completewebview/build.gradle.kts
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                // IMPORTANT: This must match your GitHub username
                groupId = "com.github.owaisraza10"
                artifactId = "completewebview"
                version = "1.0.0"
            }
        }
    }
}