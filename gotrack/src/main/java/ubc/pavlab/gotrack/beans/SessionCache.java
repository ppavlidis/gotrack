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

package ubc.pavlab.gotrack.beans;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;

import org.apache.log4j.Logger;
import org.primefaces.model.chart.LineChartModel;

import ubc.pavlab.gotrack.model.ChartTuple;
import ubc.pavlab.gotrack.model.Edition;
import ubc.pavlab.gotrack.model.EvidenceReference;
import ubc.pavlab.gotrack.model.Gene;
import ubc.pavlab.gotrack.model.GeneOntologyTerm;
import ubc.pavlab.gotrack.model.GoChart;
import ubc.pavlab.gotrack.model.GraphTypeKey;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@ManagedBean
@SessionScoped
public class SessionCache {

    private static final Logger log = Logger.getLogger( SessionCache.class );
    private final int MAX_ENTRIES = 5;

    private Map<Gene, ChartTuple> chartCache = new LinkedHashMap<Gene, ChartTuple>( MAX_ENTRIES + 1, 0.75F, true ) {
        // This method is called just after a new entry has been added
        public boolean removeEldestEntry( Map.Entry<Gene, ChartTuple> eldest ) {
            return size() > MAX_ENTRIES;
        }
    };

    private Map<Gene, Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>>> dataCache = new LinkedHashMap<Gene, Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>>>(
            MAX_ENTRIES + 1, 0.75F, true ) {
        // This method is called just after a new entry has been added
        public boolean removeEldestEntry(
                Map.Entry<Gene, Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>>> eldest ) {
            return size() > MAX_ENTRIES;
        }
    };

    public SessionCache() {
        log.info( "Cache created" );
    }

    @PostConstruct
    public void init() {
        // You can do here your initialization thing based on managed properties, if necessary.
        log.info( "SessionCache init" );
        chartCache = Collections.synchronizedMap( chartCache );
        dataCache = Collections.synchronizedMap( dataCache );
    }

    @PreDestroy
    public void destroy() {
        log.info( "SessionCache destroyed" );
    }

    public ChartTuple getCharts( Gene g ) {
        // TODO not sure if necessary, not a big deal either way
        synchronized ( chartCache ) {
            return chartCache.get( g );
        }
    }

    public void addCharts( Gene g, Map<GraphTypeKey, LineChartModel> lineChartModelMap,
            Map<GraphTypeKey, GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> goChartMap ) {
        ChartTuple ct = new ChartTuple( lineChartModelMap, goChartMap );
        synchronized ( chartCache ) {
            chartCache.put( g, ct );
        }
    }

    public Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> getData( Gene g ) {
        // TODO not sure if necessary, not a big deal either way
        synchronized ( dataCache ) {
            return dataCache.get( g );
        }
    }

    public void addData( Gene g, Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data ) {
        synchronized ( dataCache ) {
            dataCache.put( g, data );
        }
    }
}
