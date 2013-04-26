buildscript {
    repositories {
        maven { url '${mavenUrl}/prebuilts-repo' }
        maven { url '${mavenUrl}/tools-repo' }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:0.4-SNAPSHOT'
    }
}
apply plugin: 'android'

dependencies {
    compile files('libs/android-support-v4.jar')
}

android {
    compileSdkVersion ${buildApi}
    buildToolsVersion "${buildApi}"

    defaultConfig {
        minSdkVersion ${minApi}
        targetSdkVersion ${targetApi}
    }
}
