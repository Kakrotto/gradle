/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.initialization

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.InstantExecutionProblemsListener
import org.gradle.instantexecution.problems.InstantExecutionProblems
import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.PropertyTrace
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.internal.InternalListener


class DefaultInstantExecutionProblemsListener internal constructor(

    private
    val startParameter: InstantExecutionStartParameter,

    private
    val problems: InstantExecutionProblems

) : InstantExecutionProblemsListener {

    override fun onProjectAccess(invocationDescription: String, invocationSource: Any) {
        onExecutionTimeAccessProblem(invocationDescription, invocationSource)
    }

    override fun onTaskDependenciesAccess(invocationDescription: String, invocationSource: Any) {
        onExecutionTimeAccessProblem(invocationDescription, invocationSource)
    }

    private
    fun onExecutionTimeAccessProblem(invocationDescription: String, invocationSource: Any) {
        if (startParameter.isEnabled) {
            val exception = InvalidUserCodeException(
                "Invocation of '$invocationDescription' by $invocationSource at execution time is unsupported."
            )
            problems.onProblem(executionTimeAccessProblem(
                traceFor(invocationSource),
                invocationDescription,
                exception
            ))
        }
    }

    private
    fun executionTimeAccessProblem(trace: PropertyTrace, invocationDescription: String, exception: InvalidUserCodeException) =
        PropertyProblem(
            trace,
            StructuredMessage.build {
                text("invocation of ")
                reference(invocationDescription)
                text(" at execution time is unsupported.")
            },
            exception
        )

    override fun onBuildScopeListenerRegistration(listener: Any, invocationDescription: String, invocationSource: Any) {
        if (startParameter.isEnabled && listener !is InternalListener) {
            val exception = InvalidUserCodeException(
                "Listener registration '$invocationDescription' by $invocationSource is unsupported."
            )
            problems.onProblem(listenerRegistrationProblem(
                traceFor(invocationSource),
                listener,
                invocationDescription,
                exception
            ))
        }
    }

    private
    fun listenerRegistrationProblem(
        trace: PropertyTrace,
        listener: Any,
        invocationDescription: String,
        exception: InvalidUserCodeException
    ) =
        PropertyProblem(
            trace,
            StructuredMessage.build {
                text("registration of listener ")
                reference(listener.toString())
                text(" on ")
                reference(invocationDescription)
                text(" is unsupported")
            },
            exception
        )

    private
    fun traceFor(invocationSource: Any) =
        when (invocationSource) {
            is Task -> PropertyTrace.Task(
                GeneratedSubclasses.unpackType(invocationSource),
                invocationSource.path
            )
            else -> PropertyTrace.Unknown
        }
}
