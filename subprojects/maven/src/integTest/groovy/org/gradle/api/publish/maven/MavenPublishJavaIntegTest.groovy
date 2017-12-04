/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenDependencyExclusion
import spock.lang.Unroll

class MavenPublishJavaIntegTest extends AbstractMavenPublishIntegTest {
    def javaLibrary = javaLibrary(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

    def "can publish java-library with no dependencies"() {
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.assertNoDependencies()

        and:
        resolveArtifacts(javaLibrary) == ["publishTest-1.9.jar"]
        resolveApiArtifacts(javaLibrary) == ["publishTest-1.9.jar"]
        resolveRuntimeArtifacts(javaLibrary) == ["publishTest-1.9.jar"]
    }

    def "can publish java-library with dependencies"() {
        given:
        mavenRepo.module("org.test", "foo", "1.0").publish()
        mavenRepo.module("org.test", "bar", "1.0").publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:1.0"
                implementation "org.test:bar:1.0"
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.assertApiDependencies("org.test:foo:1.0")
        javaLibrary.assertRuntimeDependencies("org.test:bar:1.0")

        and:
        resolveArtifacts(javaLibrary) == ["bar-1.0.jar", "foo-1.0.jar", "publishTest-1.9.jar"]
        resolveApiArtifacts(javaLibrary) == ["foo-1.0.jar", "publishTest-1.9.jar"]
        resolveRuntimeArtifacts(javaLibrary) == ["bar-1.0.jar", "foo-1.0.jar", "publishTest-1.9.jar"]
    }

    def "can publish java-library with dependencies and excludes"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                compile "commons-collections:commons-collections:3.2.2"
                compileOnly "javax.servlet:servlet-api:2.5"
                runtime "commons-io:commons-io:1.4"
                testCompile "junit:junit:4.12"
                compile ("org.springframework:spring-core:2.5.6") {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                compile ("commons-beanutils:commons-beanutils:1.8.3") {
                   exclude group : 'commons-logging'
                }
                compile ("commons-dbcp:commons-dbcp:1.4") {
                   transitive = false
                }
                compile ("org.apache.camel:camel-jackson:2.15.3") {
                   exclude module : 'camel-core'
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["compile"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("commons-collections:commons-collections:3.2.2", "commons-io:commons-io:1.4", "org.springframework:spring-core:2.5.6", "commons-beanutils:commons-beanutils:1.8.3", "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3")
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.springframework:spring-core:2.5.6", new MavenDependencyExclusion("commons-logging", "commons-logging"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("commons-beanutils:commons-beanutils:1.8.3", new MavenDependencyExclusion("commons-logging", "*"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("commons-dbcp:commons-dbcp:1.4", new MavenDependencyExclusion("*", "*"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.apache.camel:camel-jackson:2.15.3", new MavenDependencyExclusion("*", "camel-core"))

        and:
        javaLibrary.assertApiDependencies("commons-collections:commons-collections:3.2.2", "commons-io:commons-io:1.4", "org.springframework:spring-core:2.5.6", "commons-beanutils:commons-beanutils:1.8.3", "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3")
        def apiVariant = javaLibrary.parsedModuleMetadata.variant('api')
        apiVariant.dependencies.find { it.coords == 'org.springframework:spring-core:2.5.6' }.excludes == ['commons-logging:commons-logging']
        apiVariant.dependencies.find { it.coords == 'commons-beanutils:commons-beanutils:1.8.3' }.excludes == ['commons-logging:*']
        apiVariant.dependencies.find { it.coords == 'commons-dbcp:commons-dbcp:1.4' }.excludes == ['*:*']
        apiVariant.dependencies.find { it.coords == 'org.apache.camel:camel-jackson:2.15.3' }.excludes == ['*:camel-core']

        and:
        resolveArtifacts(javaLibrary) == [
            "camel-jackson-2.15.3.jar", "commons-beanutils-1.8.3.jar", "commons-collections-3.2.2.jar", "commons-dbcp-1.4.jar", "commons-io-1.4.jar",
            "jackson-annotations-2.4.0.jar", "jackson-core-2.4.3.jar", "jackson-databind-2.4.3.jar", "jackson-module-jaxb-annotations-2.4.3.jar",
            "publishTest-1.9.jar", "spring-core-2.5.6.jar"]
    }

    def "can publish java-library with strict dependencies"() {
        given:
        createBuildScripts("""

            ${jcenterRepository()}

            dependencies {
                api "org.springframework:spring-core:2.5.6"
                implementation("commons-collections:commons-collections") {
                    version { strictly '3.2.2' }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["compile", "runtime"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("org.springframework:spring-core:2.5.6")
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:3.2.2")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                rejects()
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections:3.2.2') {
                noMoreExcludes()
                rejects ']3.2.2,)'
            }
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                rejects()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) == [
            'commons-collections-3.2.2.jar', 'commons-logging-1.1.1.jar', 'publishTest-1.9.jar', 'spring-core-2.5.6.jar'
        ]
    }

    def "can publish java-library with dependency constraints"() {
        given:
        createBuildScripts("""

            ${jcenterRepository()}

            dependencies {
                api "org.springframework:spring-core:1.2.9"
                implementation "org.apache.commons:commons-compress:1.5"
                constraints {
                    api "commons-logging:commons-logging:1.1"
                    implementation "commons-logging:commons-logging:1.2"
                    implementation("org.tukaani:xz") {
                        version { strictly "1.6" }
                    }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["compile", "runtime"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("org.springframework:spring-core:1.2.9")
        javaLibrary.parsedPom.scopes.compile.dependencies.size() == 1 //we do not publish constraints in POMs yet
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("org.apache.commons:commons-compress:1.5")
        javaLibrary.parsedPom.scopes.runtime.dependencies.size() == 1 //we do not publish constraints in POMs yet

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.springframework:spring-core:1.2.9') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-logging:commons-logging:1.1') { rejects() }

            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('org.springframework:spring-core:1.2.9') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-logging:commons-logging:1.1') { rejects() }
            constraint('commons-logging:commons-logging:1.2') { rejects() }

            dependency('org.apache.commons:commons-compress:1.5') {
                rejects()
                noMoreExcludes()
            }
            constraint('org.tukaani:xz:1.6') { rejects(']1.6,)') }

            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary, false) == [
            'commons-compress-1.5.jar', 'commons-logging-1.2.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.6.jar'
        ]

        when:
        resolveModuleMetadata = false

        then: "constraints are not published to POM files"
        resolveArtifacts(javaLibrary) == [
            'commons-compress-1.5.jar', 'commons-logging-1.0.4.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.2.jar'
        ]
    }

    def "can publish java-library with rejected versions"() {
        given:
        createBuildScripts("""

            ${jcenterRepository()}

            dependencies {
                api "org.springframework:spring-core:2.5.6"
                implementation("commons-collections:commons-collections") {
                    version { 
                        prefer '[3.2, 4)'
                        reject '3.2.1', '[3.2.2,)'
                    }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["compile", "runtime"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("org.springframework:spring-core:2.5.6")
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:[3.2, 4)")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                rejects()
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections:[3.2, 4)') {
                noMoreExcludes()
                rejects '3.2.1', '[3.2.2,)'
            }
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                rejects()
            }
            noMoreDependencies()
        }

        then:
        resolveArtifacts(javaLibrary, false) == [
            'commons-collections-3.2.jar', 'commons-logging-1.1.1.jar', 'publishTest-1.9.jar', 'spring-core-2.5.6.jar'
        ]

        when:
        resolveModuleMetadata = false

        then:
        resolveArtifacts(javaLibrary, false) == [
            'commons-collections-3.2.2.jar', 'commons-logging-1.1.1.jar', 'publishTest-1.9.jar', 'spring-core-2.5.6.jar'
        ]

    }

    def "can publish java-library with dependencies without version"() {
        given:
        createBuildScripts("""

            ${jcenterRepository()}

            dependencies {
                implementation "commons-collections:commons-collections"
                constraints {
                    implementation "commons-collections:commons-collections:3.2.2"
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["runtime"] as Set
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:")
        javaLibrary.parsedPom.scopes.runtime.dependencies.size() == 1 //we do not publish constraints in POMs yet

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-collections:commons-collections:3.2.2') { rejects() }
            noMoreDependencies()
        }

        when:
        // This currently fails, because the POM does not provide a version, and we don't yet publish constraints to POM files.
        resolveArtifacts(javaLibrary, false, true)

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':resolve'")
        failure.assertHasCause("Could not find commons-collections:commons-collections:")
    }

    def "can publish java-library with attached artifacts"() {
        given:
        createBuildScripts("""
            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                classifier "source"
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        artifact sourceJar
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.withClassifiedArtifact("source", "jar").assertPublished()

        and:
        resolveArtifacts(javaLibrary) == ["publishTest-1.9.jar"]
        resolveArtifacts(javaLibrary, [classifier: 'source']) == ["publishTest-1.9-source.jar", "publishTest-1.9.jar"]
    }

    def "can publish java-library-platform with dependencies and constraints"() {
        given:
        mavenRepo.module("org.test", "foo", "1.0").publish()
        mavenRepo.module("org.test", "bar", "1.0").publish()
        mavenRepo.module("org.test", "bar", "1.1").publish()

        createBuildScripts("""
            dependencies {
                api "org.test:bar:1.0"
                implementation "org.test:foo:1.0"
                
                constraints {
                    implementation "org.test:bar:1.1"
                }
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaLibraryPlatform
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        def mavenModule = javaLibrary.mavenModule

        mavenModule.assertPublished()
        mavenModule.assertArtifactsPublished("publishTest-1.9.module", "publishTest-1.9.pom")

        // No files are published for either variant
        with(javaLibrary.parsedModuleMetadata) {
            variants*.name as Set == ['api', 'runtime'] as Set
            variant('api').files.empty
            variant('runtime').files.empty
        }

        // Published with pom packaging
        assert javaLibrary.parsedPom.packaging == 'pom'

        javaLibrary.assertApiDependencies("org.test:bar:1.0")
        javaLibrary.assertRuntimeDependencies("org.test:foo:1.0")

        and:
        resolveArtifacts(javaLibrary, false) == ["bar-1.1.jar", "foo-1.0.jar"]
        resolveApiArtifacts(javaLibrary) == ["bar-1.0.jar"]
        resolveRuntimeArtifacts(javaLibrary) == ["bar-1.1.jar", "foo-1.0.jar"]

        when:
        resolveModuleMetadata = false

        then: "constraints are not published to POM files"
        resolveArtifacts(javaLibrary) == ["bar-1.0.jar", "foo-1.0.jar"]
    }

    @Unroll("'#gradleConfiguration' dependencies end up in '#mavenScope' scope with '#plugin' plugin")
    void "maps dependencies in the correct Maven scope"() {
        given:
        file("settings.gradle") << '''
            rootProject.name = 'publishTest' 
            include "b"
        '''
        buildFile << """
            apply plugin: "$plugin"
            apply plugin: "maven-publish"

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            
            dependencies {
                $gradleConfiguration project(':b')
            }
        """

        file('b/build.gradle') << """
            apply plugin: 'java'
            
            group = 'org.gradle.test'
            version = '1.2'
            
        """

        when:
        succeeds "publish"

        then:
        javaLibrary.assertPublished()
        if (mavenScope == 'compile') {
            javaLibrary.assertApiDependencies('org.gradle.test:b:1.2')
        } else {
            javaLibrary.assertRuntimeDependencies('org.gradle.test:b:1.2')
        }

        where:
        plugin         | gradleConfiguration  | mavenScope
        'java'         | 'compile'            | 'compile'
        'java'         | 'runtime'            | 'compile'
        'java'         | 'implementation'     | 'runtime'
        'java'         | 'runtimeOnly'        | 'runtime'

        'java-library' | 'api'                | 'compile'
        'java-library' | 'compile'            | 'compile'
        'java-library' | 'runtime'            | 'compile'
        'java-library' | 'runtimeOnly'        | 'runtime'
        'java-library' | 'implementation'     | 'runtime'

    }

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-library'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            group = 'org.gradle.test'
            version = '1.9'

$append
"""

    }

}
