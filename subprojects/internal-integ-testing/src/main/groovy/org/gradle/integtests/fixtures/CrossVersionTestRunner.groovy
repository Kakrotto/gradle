/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures

import org.gradle.util.GradleVersion

/**
 * <p>Executes instances of {@link CrossVersionIntegrationSpec} against each previous Gradle version.
 *
 * <p>Sets the {@link CrossVersionIntegrationSpec#previous} property of the test instance before executing it.
 *
 * <p>A test class can be annotated with {@link TargetVersions} to specify the set of versions the test is compatible with, and {@link IgnoreVersions} to specify the set of versions the
 * test should not be run for.
 */
class CrossVersionTestRunner extends AbstractCompatibilityTestRunner {
    CrossVersionTestRunner(Class<? extends CrossVersionIntegrationSpec> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        for (BasicGradleDistribution version : previous) {
            add(new PreviousVersionExecution(version, isEnabled(version)))
        }
    }

    protected boolean isEnabled(BasicGradleDistribution previousVersion) {
        Closure ignoreVersions = getAnnotationClosure(target, IgnoreVersions, {})
        if (ignoreVersions(previousVersion)) {
            return false
        }

        def versionsAnnotation = target.getAnnotation(TargetVersions)
        if (versionsAnnotation == null) {
            return true
        }

        List<String> targetGradleVersions = versionsAnnotation.value()
        for (String targetGradleVersion : targetGradleVersions) {
            if (isMatching(targetGradleVersion, previousVersion.version)) {
                return true
            }
        }
        return false
    }

    private static boolean isMatching(String targetGradleVersion, String candidate) {
        if (targetGradleVersion.endsWith('+')) {
            def minVersion = targetGradleVersion.substring(0, targetGradleVersion.length() - 1)
            return GradleVersion.version(minVersion) <= GradleVersion.version(candidate)
        }
        return targetGradleVersion == candidate
    }

    private static Closure getAnnotationClosure(Class target, Class annotation, Closure defaultValue) {
        def a = target.getAnnotation(annotation)
        a ? a.value().newInstance(target, target) : defaultValue
    }

    private static class PreviousVersionExecution extends AbstractMultiTestRunner.Execution {
        final BasicGradleDistribution previousVersion
        final boolean enabled

        PreviousVersionExecution(BasicGradleDistribution previousVersion, boolean enabled) {
            this.previousVersion = previousVersion
            this.enabled = enabled
        }

        @Override
        String getDisplayName() {
            return previousVersion.version
        }

        @Override
        protected void before() {
            target.previous = previousVersion
        }

        @Override
        protected boolean isEnabled() {
            return enabled
        }
    }
}
