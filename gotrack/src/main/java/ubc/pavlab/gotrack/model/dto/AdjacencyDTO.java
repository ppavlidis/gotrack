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

package ubc.pavlab.gotrack.model.dto;

/**
 * Data Transfer Object for {@link Relation}
 * 
 * @author mjacobson
 * @version $Id$
 */
public class AdjacencyDTO {

    private final Integer goEdition;
    private final String child;
    private final String parent;
    private final String type;

    public AdjacencyDTO( Integer goEdition, String child, String parent, String type ) {
        super();
        this.goEdition = goEdition;
        this.child = child;
        this.parent = parent;
        this.type = type;
    }

    public Integer getGoEdition() {
        return goEdition;
    }

    public String getChild() {
        return child;
    }

    public String getParent() {
        return parent;
    }

    public String getType() {
        return type;
    }

}
