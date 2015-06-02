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
 * Data Transfer Object for enrichment queries
 * 
 * @author mjacobson
 * @version $Id$
 */
public final class EnrichmentDTO {

    private final Integer edition;
    private final String symbol;
    private final String goId;

    /**
     * @param date
     * @param edition
     * @param goEdition
     * @param symbol
     * @param goId
     * @param name
     * @param aspect
     */
    public EnrichmentDTO( Integer edition, String symbol, String goId ) {
        super();
        this.edition = edition;
        this.symbol = symbol;
        this.goId = goId;
    }

    public Integer getEdition() {
        return edition;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getGoId() {
        return goId;
    }

}
