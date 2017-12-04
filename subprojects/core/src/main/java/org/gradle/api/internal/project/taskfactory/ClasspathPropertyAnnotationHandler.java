/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.changedetection.state.ClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.tasks.DeclaredTaskInputFileProperty;
import org.gradle.api.internal.tasks.InputsOutputVisitor;
import org.gradle.api.internal.tasks.PropertyInfo;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.InputFiles;

import java.lang.annotation.Annotation;

public class ClasspathPropertyAnnotationHandler implements OverridingPropertyAnnotationHandler, FileSnapshottingPropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Classpath.class;
    }

    @Override
    public Class<? extends Annotation> getOverriddenAnnotationType() {
        return InputFiles.class;
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getSnapshotterImplementationType() {
        return ClasspathSnapshotter.class;
    }

    @Override
    public void attachActions(final TaskPropertyActionContext context) {
    }

    @Override
    public void accept(PropertyInfo propertyInfo, InputsOutputVisitor visitor, PropertySpecFactory specFactory) {
        DeclaredTaskInputFileProperty fileSpec = specFactory.createInputFileSpec(propertyInfo, ValidationAction.NO_OP);
        fileSpec
            .withPropertyName(propertyInfo.getName())
            .withNormalizer(ClasspathNormalizer.class)
            .optional(propertyInfo.isOptional());
        visitor.visitInputFileProperty(fileSpec);
    }
}
