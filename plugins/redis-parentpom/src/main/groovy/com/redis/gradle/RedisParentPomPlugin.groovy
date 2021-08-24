/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2021 Julien Ruaux.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redis.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.kordamp.gradle.plugin.base.ProjectConfigurationExtension
import org.kordamp.gradle.plugin.profiles.ProfilesExtension
import org.kordamp.gradle.plugin.project.java.JavaProjectPlugin

/**
 * @author Andres Almiray
 */
class RedisParentPomPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.apply(JavaProjectPlugin)

        if (!project.hasProperty('sonatypeReleaseRepositoryUrl')) project.ext.sonatypeReleaseRepositoryUrl = 'https://oss.sonatype.org/service/local/'
        if (!project.hasProperty('sonatypeSnapshotRepositoryUrl')) project.ext.sonatypeSnapshotRepositoryUrl = 'https://oss.sonatype.org/content/repositories/snapshots/'
        if (!project.hasProperty('sonatypeUsername')) project.ext.sonatypeUsername = '**undefined**'
        if (!project.hasProperty('sonatypePassword')) project.ext.sonatypePassword = '**undefined**'

        project.extensions.findByType(ProjectConfigurationExtension).with {
            release = (project.rootProject.findProperty('release') ?: false).toBoolean()

            info {
                vendor = 'Redis'

                links {
                    website      = "https://github.com/redis-developer/${project.rootProject.name}"
                    issueTracker = "https://github.com/redis-developer/${project.rootProject.name}/issues"
                    scm          = "https://github.com/redis-developer/${project.rootProject.name}.git"
                }

                scm {
                    url                 = "https://github.com/redis-developer/${project.rootProject.name}"
                    connection          = "scm:git:https://github.com/redis-developer/${project.rootProject.name}.git"
                    developerConnection = "scm:git:git@github.com:redis-developer/${project.rootProject.name}.git"
                }

                people {
                    person {
                        id    = 'jruaux'
                        name  = 'Julien Ruaux'
                        url   = 'https://github.com/jruaux'
                        roles = ['developer']
                        properties = [
                            twitter: 'TypeErasure',
                            github : 'jruaux'
                        ]
                    }
                }

                credentials {
                    sonatype {
                        username = project.sonatypeUsername
                        password = project.sonatypePassword
                    }
                }

                repositories {
                    repository {
                        name = 'localRelease'
                        url  = "${project.rootProject.buildDir}/repos/local/release"
                    }
                    repository {
                        name = 'localSnapshot'
                        url  = "${project.rootProject.buildDir}/repos/local/snapshot"
                    }
                }
            }

            licensing {
                licenses {
                    license {
                        id = 'Apache-2.0'
                    }
                }
            }

            docs {
                javadoc {
                    excludes = ['**/*.html', 'META-INF/**']
                }
                sourceXref {
                    inputEncoding = 'UTF-8'
                }
            }

            publishing {
                releasesRepository  = 'localRelease'
                snapshotsRepository = 'localSnapshot'
            }
        }

        project.extensions.findByType(ProfilesExtension).with {
            profile('release') {
                activation {
                    property {
                        key = 'full-release'
                    }
                }
                action {
                    config {
                        release = true
                    }
                }
            }

            profile('sign') {
                activation {
                    property {
                        key = 'full-release'
                    }
                }
                action {
                    println 'Artifact signing is turned ON'

                    config {
                        publishing {
                            signing {
                                enabled = true
                                keyId = System.getenv()['GPG_KEY_ID']
                                secretKey = System.getenv()['GPG_SECRET_KEY']
                                password = System.getenv()['GPG_PASSPHRASE']
                            }
                        }
                    }
                }
            }

            profile('stage') {
                activation {
                    property {
                        key = 'full-release'
                    }
                }
                action {
                    println 'Staging to Sonatype is turned ON'

                    apply plugin: 'io.github.gradle-nexus.publish-plugin'

                    nexusPublishing {
                        repositories {
                            sonatype {
                                username = project.ext.sonatypeUsername
                                password = project.ext.sonatypePassword
                                nexusUrl = uri(project.ext.sonatypeReleaseRepositoryUrl)
                                snapshotRepositoryUrl = uri(project.ext.sonatypeSnapshotRepositoryUrl)
                            }
                        }
                    }
                }
            }
        }

        project.allprojects {
            repositories {
                mavenCentral()
            }

            normalization {
                runtimeClasspath {
                    ignore('/META-INF/MANIFEST.MF')
                }
            }

            dependencyUpdates.resolutionStrategy {
                componentSelection { rules ->
                    rules.all { selection ->
                        boolean rejected = ['alpha', 'beta', 'rc', 'cr'].any { qualifier ->
                            selection.candidate.version ==~ /(?i).*[.-]${qualifier}[.\d-]*.*/
                        }
                        if (rejected) {
                            selection.reject('Release candidate')
                        }
                    }
                }
            }
        }

        project.allprojects { Project p ->
            def scompat = project.findProperty('sourceCompatibility')
            def tcompat = project.findProperty('targetCompatibility')

            p.tasks.withType(JavaCompile) { JavaCompile c ->
                if (scompat) c.sourceCompatibility = scompat
                if (tcompat) c.targetCompatibility = tcompat
            }
            p.tasks.withType(GroovyCompile) { GroovyCompile c ->
                if (scompat) c.sourceCompatibility = scompat
                if (tcompat) c.targetCompatibility = tcompat
            }
        }
    }
}
