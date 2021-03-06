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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;

public class DefaultArtifactTransforms implements ArtifactTransforms {
    private final VariantAttributeMatchingCache matchingCache;
    private final AttributesSchemaInternal schema;

    public DefaultArtifactTransforms(VariantAttributeMatchingCache matchingCache, AttributesSchemaInternal schema) {
        this.matchingCache = matchingCache;
        this.schema = schema;
    }

    public VariantSelector variantSelector(AttributeContainerInternal consumerAttributes, boolean allowNoMatchingVariants) {
        return new AttributeMatchingVariantSelector(matchingCache, schema, consumerAttributes.asImmutable(), allowNoMatchingVariants);
    }

}
