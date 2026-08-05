pluginManagement {
    repositories {
        // 冷启动时允许复用本机已校验的 Maven 缓存；稳定后会删除这个临时仓库块。
        maven {
            name = "RTSBuilding verified bootstrap cache"
            url = uri("C:/Users/ping/.m2/repository")
            content {
                includeModule("org.jetbrains.kotlin", "kotlin-gradle-plugin")
                includeModule("com.diffplug.spotless", "spotless-plugin-gradle")
                includeModule("com.diffplug.spotless", "spotless-lib")
                includeModule("com.diffplug.spotless", "spotless-lib-extra")
                includeGroup("org.eclipse.jgit")
                includeGroup("com.googlecode.concurrent-trees")
                includeGroup("dev.equo.ide")
                includeGroup("org.eclipse.platform")
            }
            metadataSources {
                gradleMetadata()
                mavenPom()
                artifact()
            }
        }
        flatDir {
            dirs("C:/Users/ping/.m2/rtsbuilding-bootstrap-flat")
        }
        maven {
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version("2.0.20")
}
