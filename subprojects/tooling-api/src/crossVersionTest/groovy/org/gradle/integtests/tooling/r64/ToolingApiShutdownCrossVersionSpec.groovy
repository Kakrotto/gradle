/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r64

import org.gradle.integtests.tooling.CancellationSpec
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion(">=6.4")
class ToolingApiShutdownCrossVersionSpec extends CancellationSpec {

    def "can forcibly stop a project connection when running a build"() {
        def existingDaemonPids = toolingApi.daemons.daemons.collect { it.context.pid }

        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            connection.stopNow()
            resultHandler.finished()
        }
        def newDaemons = toolingApi.daemons.daemons.findAll { !existingDaemonPids.contains(it.context.pid) }

        then:
        newDaemons.size() == 1
        newDaemons[0].stops()
        noExceptionThrown()
    }

    def "can forcibly stop a project connection when querying a tooling model"() {
        def existingDaemonPids = toolingApi.daemons.daemons.collect { it.context.pid }

        toolingApi.requireDaemons()
        buildFile << """
            apply plugin: 'eclipse'
            eclipse {
                project {
                    file {
                        whenMerged {
                            ${server.callFromBuild("waiting")}
                        }
                    }
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def query = connection.model(EclipseProject)
            query.get(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            connection.stopNow()
            resultHandler.finished()
        }
        def newDaemons = toolingApi.daemons.daemons.findAll { !existingDaemonPids.contains(it.context.pid) }

        then:
        newDaemons.size() == 1
        newDaemons[0].stops()
        noExceptionThrown()
    }
}
