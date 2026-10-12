buildscript {
	dependencies {
		// Keep built-in Kotlin and the Compose compiler on the same version.
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
	}
}

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.compose) apply false
}
