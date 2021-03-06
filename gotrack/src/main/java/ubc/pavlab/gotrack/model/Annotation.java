/*
 * The gotrack project
 * 
 * Copyright (c) 2015 University of British Columbia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ubc.pavlab.gotrack.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ubc.pavlab.gotrack.model.go.GeneOntologyTerm;

/**
 * Represents an annotation, only makes sense when used to connect a {@link GeneOntologyTerm} and {@link Gene}
 * 
 * @author mjacobson
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
public final class Annotation {

    private final String qualifier;
    private final Evidence evidence;
    private final String reference;

}
