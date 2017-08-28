/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift

import groovy.io.FileType
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.Matchers.containsText

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftExecutableIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "skip compile, link and install tasks when no source"() {
        given:
        buildFile << """
            apply plugin: 'swift-executable'
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(":linkMain")
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        and:
        file("src/main/swift/broken.swift") << "broken!"

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileSwift'.");
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }

    def "sources are compiled and linked with Swift tools"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        executable("build/exe/App").assertExists()
        installation("build/install/App").exec().out == app.expectedOutput
    }

    def "removes stale object files"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        and:
        succeeds "assemble"
        file(app.multiply.sourceFile.withPath("src/main")).delete()
        file(app.greeter.sourceFile.withPath("src/main")).renameTo(file("src/main/swift/renamed-greeter.swift"))

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")
        result.assertTasksNotSkipped(":compileSwift", ":linkMain", ":installMain", ":assemble")

        file("build/main/objs").eachFileRecurse(FileType.FILES) {
            assert it.name != app.multiply.sourceFile.name.replace('.swift', '.o')
            assert it.name != app.greeter.sourceFile.name.replace('.swift', '.o')
        }
        executable("build/exe/App").assertExists()
        installation("build/install/App").exec().out == app.expectedOutput
    }

    def "removes stale executable file"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        and:
        succeeds "assemble"
        testDirectory.file("src").deleteDir()

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")
        result.assertTasksSkipped(":linkMain")

        executable("build/exe/App").assertDoesNotExist()
        installation("build/install/App").assertNotInstalled()
    }

    def "ignores non-Swift source files in source directory"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)
        file("src/main/swift/ignore.cpp") << 'broken!'
        file("src/main/swift/ignore.c") << 'broken!'
        file("src/main/swift/ignore.m") << 'broken!'
        file("src/main/swift/ignore.h") << 'broken!'
        file("src/main/swift/ignore.java") << 'broken!'

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        executable("build/exe/App").assertExists()
        installation("build/install/App").exec().out == app.expectedOutput
    }

    def "build logic can change source layout convention"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToSourceDir(file("Sources"))
        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            executable {
                source.from 'Sources'
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        file("build/main/objs").assertIsDir()
        executable("build/exe/App").assertExists()
        installation("build/install/App").exec().out == app.expectedOutput
    }

    def "build logic can add individual source files"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.main.writeToSourceDir(file("src/main.swift"))
        app.greeter.writeToSourceDir(file("src/one.swift"))
        app.sum.writeToSourceDir(file("src/two.swift"))
        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            executable {
                source {
                    from('src/main.swift')
                    from('src/one.swift')
                    from('src/two.swift')
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        file("build/main/objs").assertIsDir()
        executable("build/exe/App").assertExists()
        installation("build/install/App").exec().out == app.expectedOutput
    }

    def "build logic can change buildDir"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        !file("build").exists()
        file("output/main/objs").assertIsDir()
        executable("output/exe/App").assertExists()
        installation("output/install/App").exec().out == app.expectedOutput
    }

    def "build logic can define the module name"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            executable.module = 'TestApp'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        file("build/main/objs").assertIsDir()
        executable("build/exe/TestApp").assertExists()
        installation("build/install/TestApp").exec().out == app.expectedOutput
    }

    def "build logic can change task output locations"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            compileSwift.objectFileDirectory = layout.buildDirectory.dir("object-files")
            linkMain.binaryFile = layout.buildDirectory.file("exe/some-app.exe")
            installMain.installDirectory = layout.buildDirectory.dir("some-app")
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        file("build/object-files").assertIsDir()
        file("build/exe/some-app.exe").assertIsFile()
        installation("build/some-app").exec().out == app.expectedOutput
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileSwift", ":greeter:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")

        executable("app/build/exe/App").assertExists()
        sharedLibrary("greeter/build/lib/Greeter").assertExists()
        installation("app/build/install/App").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/App/lib/Greeter").assertExists()
    }

    def "can compile and link against library with API dependencies"() {
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":hello:linkMain", ":log:compileSwift", ":log:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")

        sharedLibrary("hello/build/lib/Hello").assertExists()
        sharedLibrary("log/build/lib/Log").assertExists()
        executable("app/build/exe/App").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/App/lib/Hello").assertExists()
        sharedLibrary("app/build/install/App/lib/Log").assertExists()
    }

    def "honors changes to library buildDir"() {
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
                buildDir = 'out'
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":hello:linkMain", ":log:compileSwift", ":log:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")

        !file("log/build").exists()
        sharedLibrary("hello/build/lib/Hello").assertExists()
        sharedLibrary("log/out/lib/Log").assertExists()
        executable("app/build/exe/App").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/App/lib/Hello").file.assertExists()
        sharedLibrary("app/build/install/App/lib/Log").file.assertExists()
    }

    def "multiple components can share the same source directory"() {
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':hello')
                }
                executable {
                    source.from '../Sources/${app.main.sourceFile.name}'
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':log')
                }
                library {
                    source.from '../Sources/${app.greeter.sourceFile.name}'
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
                library {
                    source.from '../Sources/${app.logger.sourceFile.name}'
                }
            }
"""
        app.library.writeToSourceDir(file("Sources"))
        app.logLibrary.writeToSourceDir(file("Sources"))
        app.executable.writeToSourceDir(file("Sources"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":hello:linkMain", ":log:compileSwift", ":log:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")

        sharedLibrary("hello/build/lib/Hello").assertExists()
        sharedLibrary("log/build/lib/Log").assertExists()
        executable("app/build/exe/App").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/App/lib/Hello").file.assertExists()
        sharedLibrary("app/build/install/App/lib/Log").file.assertExists()
    }

    def "can compile and link against libraries in included builds"() {
        settingsFile << """
            rootProject.name = 'app'
            includeBuild 'hello'
            includeBuild 'log'
        """
        file("hello/settings.gradle") << "rootProject.name = 'hello'"
        file("log/settings.gradle") << "rootProject.name = 'log'"

        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            apply plugin: 'swift-executable'
            dependencies {
                implementation 'test:hello:1.2'
            }
        """
        file("hello/build.gradle") << """
            apply plugin: 'swift-library'
            group = 'test'
            dependencies {
                api 'test:log:1.4'
            }
        """
        file("log/build.gradle") << """
            apply plugin: 'swift-library'
            group = 'test'
        """

        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.executable.writeToProject(testDirectory)

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":hello:linkMain", ":log:compileSwift", ":log:linkMain", ":compileSwift", ":linkMain", ":installMain", ":assemble")

        sharedLibrary("hello/build/lib/Hello").assertExists()
        sharedLibrary("log/build/lib/Log").assertExists()
        executable("build/exe/App").assertExists()
        installation("build/install/App").exec().out == app.expectedOutput
        sharedLibrary("build/install/App/lib/Hello").file.assertExists()
        sharedLibrary("build/install/App/lib/Log").file.assertExists()
    }
}
