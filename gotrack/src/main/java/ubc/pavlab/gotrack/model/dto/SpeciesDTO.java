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

import ubc.pavlab.gotrack.model.Species;

/**
 * Data Transfer Object for {@link Species}
 * 
 * @author mjacobson
 * @version $Id$
 */
public final class SpeciesDTO {

    private final Integer id;
    private final String commonName;
    private final String scientificName;
    private final Integer taxon;
    private final Integer interactingTaxon;

    public SpeciesDTO( Integer id, String commonName, String scientificName, Integer taxon, Integer interactingTaxon ) {
        super();
        this.id = id;
        this.commonName = commonName;
        this.scientificName = scientificName;
        this.taxon = taxon;
        this.interactingTaxon = interactingTaxon;
    }

    public Integer getId() {
        return id;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getScientificName() {
        return scientificName;
    }

    public Integer getTaxon() {
        return taxon;
    }

    public Integer getInteractingTaxon() {
        return interactingTaxon;
    }

}
