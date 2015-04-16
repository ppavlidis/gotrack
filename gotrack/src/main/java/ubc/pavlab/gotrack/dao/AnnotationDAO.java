/*
 * The gotrack project
 * 
 * Copyright (c) 2014 University of British Columbia
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

package ubc.pavlab.gotrack.dao;

import java.util.Map;
import java.util.Set;

import ubc.pavlab.gotrack.model.Accession;
import ubc.pavlab.gotrack.model.AnnotationDetailed;
import ubc.pavlab.gotrack.model.Edition;
import ubc.pavlab.gotrack.model.EvidenceReference;
import ubc.pavlab.gotrack.model.Gene;
import ubc.pavlab.gotrack.model.GeneOntologyTerm;

/**
 * This interface represents a contract for a DAO for the {@link AnnotationDetailed} model. Note that all methods are
 * read-only.
 */
public interface AnnotationDAO {

    // Actions ------------------------------------------------------------------------------------

    public Map<Edition, Map<Gene, Set<GeneOntologyTerm>>> enrichmentData( Integer species, Set<Gene> genes )
            throws DAOException;

    public Map<Accession, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> track( Integer species,
            String symbol ) throws DAOException;

    public Map<Accession, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> trackPropagate( Integer species,
            String symbol ) throws DAOException;

    public Map<Edition, Map<Gene, Set<GeneOntologyTerm>>> enrichmentDataPropagate( Integer species, Set<Gene> genes )
            throws DAOException;

    public Map<Edition, Map<GeneOntologyTerm, Integer>> enrichmentDataPropagateCountsOnly( Integer species,
            Set<Gene> genes ) throws DAOException;

    public Map<Edition, Integer> enrichmentSampleSizes( Integer species, Set<Gene> genes ) throws DAOException;

}