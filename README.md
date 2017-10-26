Tracepot Gradle Plugin
-------------------

This plugin integrates Tracepot service with the Gradle build system. With this plugin, you can automatically upload proguard mapping file, application icon and name to Tracepot after each build. Tested with Android Studio.

Installation
---------

A typical Tracepot Gradle Plugin installation takes less than 20 seconds. Installation consists of adding the following to your ***build.gradle*** file:

 1. Add the Tracepot Maven repository:

        maven { url 'http://www.tracepot.com/maven' }
    
 2. Add plugin dependency: 

        classpath 'com.tracepot.plugins.gradle:tracepot:3.+'

 3. Apply plugin:

        apply plugin: 'tracepot'

 4. Configure your Tracepot Group API key and build flavors by adding this to your "*android*" section: (You can find your API key in the group settings)

        tracepotConfig {
            apiGroupKey "1234567890abcdef"
            enabledFor "release"
        }

    This will enable the plugin for the `release` build variant. If you have multiple flavors you can separate build variants by comma:

        enabledFor "freeRelease", "proRelease"

Complete Example
----------------

For convenience, here is a snippet of a complete ***build.gradle*** file, including the additions above.

    buildscript {
        repositories {
            jcenter()
            maven { url 'http://www.tracepot.com/maven' }
        }
    
        dependencies {
            classpath 'com.android.tools.build:gradle:3.0.0'
            classpath 'com.tracepot.plugins.gradle:tracepot:3.+'
        }
    }
    
    apply plugin: 'com.android.application'
    apply plugin: 'tracepot'
    
    android {
        tracepotConfig {
            apiGroupKey "1234567890abcdef"
            enabledFor "release"
        }
    }

Android Studio 3
----------------

Android Studio 3 comes with Android Plugin for Gradle 3.0 which enables AAPT2 by default. With AAPT2 enabled this plugin will be able to upload only proguard mapping file without application name and icon. To upload name, icon and mapping file you need to disable AAPT2.

To disable AAPT2 add this to your ***gradle.properties*** file:

    android.enableAapt2=false


Usage
-----

This plugin will run automatically for each build variant you have enabled in the config above.

If the application does not exist on Tracepot it will be automatically added.

If you are using `applicationIdSuffix '.debug'` in your config, Tracepot will strip the `.debug` part when adding the application.


Using a Web Proxy
--------------------------------

Behind a firewall at work? Tracepot Gradle Plugin supports HTTP proxy via "*http.proxyHost*" system property. Please refer to the [Accessing The Web Via a Proxy](http://www.gradle.org/docs/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy) section in the Gradle user guide document.

Bugs
----

Please send bug reports to support@tracepot.com
