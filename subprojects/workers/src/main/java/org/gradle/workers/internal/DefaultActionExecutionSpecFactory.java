/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.workers.WorkerExecution;
import org.gradle.workers.WorkerParameters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class DefaultActionExecutionSpecFactory implements ActionExecutionSpecFactory {
    private final IsolatableFactory isolatableFactory;
    private final IsolatableSerializerRegistry serializerRegistry;
    private final InstantiatorFactory instantiatorFactory;

    public DefaultActionExecutionSpecFactory(IsolatableFactory isolatableFactory, IsolatableSerializerRegistry serializerRegistry, InstantiatorFactory instantiatorFactory) {
        this.isolatableFactory = isolatableFactory;
        this.serializerRegistry = serializerRegistry;
        this.instantiatorFactory = instantiatorFactory;
    }

    @Override
    public <T extends WorkerParameters> TransportableActionExecutionSpec<T> newTransportableSpec(ActionExecutionSpec<T> spec) {
        if (spec instanceof IsolatedParametersActionExecutionSpec) {
            IsolatedParametersActionExecutionSpec isolatedSpec = (IsolatedParametersActionExecutionSpec) spec;
            return new TransportableActionExecutionSpec<T>(isolatedSpec.getDisplayName(), isolatedSpec.getImplementationClass().getName(), serialize(isolatedSpec.getIsolatedParams()), isolatedSpec.getClassLoaderStructure());
        } else if (spec instanceof TransportableActionExecutionSpec) {
            return (TransportableActionExecutionSpec<T>) spec;
        } else {
            throw new IllegalArgumentException("Can't create a TransportableActionExecutionSpec from spec with type: " + spec.getClass().getSimpleName());
        }
    }

    @Override
    public <T extends WorkerParameters> IsolatedParametersActionExecutionSpec<T> newIsolatedSpec(String displayName, Class<? extends WorkerExecution<T>> implementationClass, T params, ClassLoaderStructure classLoaderStructure) {
        return new IsolatedParametersActionExecutionSpec<T>(implementationClass, displayName, isolatableFactory.isolate(params), classLoaderStructure);
    }

    @Override
    public <T extends WorkerParameters> SimpleActionExecutionSpec<T> newSimpleSpec(ActionExecutionSpec<T> spec) {
        if (spec instanceof TransportableActionExecutionSpec) {
            TransportableActionExecutionSpec<T> transportableSpec = (TransportableActionExecutionSpec<T>) spec;
            T params = Cast.uncheckedCast(deserialize(transportableSpec.getSerializedParameters()).isolate());
            return new SimpleActionExecutionSpec<T>(Cast.uncheckedCast(fromClassName(transportableSpec.getImplementationClassName())), transportableSpec.getDisplayName(), params, transportableSpec.getClassLoaderStructure());
        } else if (spec instanceof IsolatedParametersActionExecutionSpec) {
            IsolatedParametersActionExecutionSpec<T> isolatedSpec = (IsolatedParametersActionExecutionSpec<T>) spec;
            T params = Cast.uncheckedCast(isolatedSpec.getIsolatedParams().isolate());
            return new SimpleActionExecutionSpec<T>(isolatedSpec.getImplementationClass(), isolatedSpec.getDisplayName(), params, isolatedSpec.getClassLoaderStructure());
        } else {
            throw new IllegalArgumentException("Can't create a SimpleActionExecutionSpec from spec with type: " + spec.getClass().getSimpleName());
        }
    }

    Class<?> fromClassName(String className) {
        try {
            return ClassLoaderUtils.classFromContextLoader(className);
        } catch (Exception e) {
            throw new WorkSerializationException("Could not deserialize unit of work.", e);
        }
    }

    private byte[] serialize(Isolatable<?> isolatable) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        KryoBackedEncoder encoder = new KryoBackedEncoder(outputStream);
        try {
            serializerRegistry.writeIsolatable(encoder, isolatable);
            encoder.flush();
        } catch (Exception e) {
            throw new WorkSerializationException("Could not serialize unit of work.", e);
        }
        return outputStream.toByteArray();
    }

    private Isolatable<?> deserialize(byte[] bytes) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        KryoBackedDecoder decoder = new KryoBackedDecoder(inputStream);
        try {
            return serializerRegistry.readIsolatable(decoder);
        } catch (Exception e) {
            throw new WorkSerializationException("Could not deserialize unit of work.", e);
        }
    }
}
