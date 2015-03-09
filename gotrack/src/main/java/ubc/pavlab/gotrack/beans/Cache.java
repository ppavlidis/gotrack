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

package ubc.pavlab.gotrack.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import ubc.pavlab.gotrack.dao.CacheDAO;
import ubc.pavlab.gotrack.dao.SpeciesDAO;
import ubc.pavlab.gotrack.model.Accession;
import ubc.pavlab.gotrack.model.Edition;
import ubc.pavlab.gotrack.model.Species;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@ManagedBean(eager = true)
@ApplicationScoped
public class Cache implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -113622419234682946L;

    private static final Logger log = Logger.getLogger( Cache.class );

    @ManagedProperty("#{settingsCache}")
    private SettingsCache settingsCache;

    @ManagedProperty("#{daoFactoryBean}")
    private DAOFactoryBean daoFactoryBean;

    private static List<Species> speciesList;
    private static Map<Integer, Edition> currentEditions = new HashMap<Integer, Edition>();
    private static Map<Integer, LinkedList<Edition>> allEditions = new HashMap<Integer, LinkedList<Edition>>();
    private static Map<Integer, Map<String, Accession>> currrentAccessions = new HashMap<Integer, Map<String, Accession>>();
    // private static Map<Integer, Collection<String>> symbols = new HashMap<Integer, Collection<String>>();
    private static Map<Integer, Map<String, Collection<Accession>>> symbolToCurrentAccessions = new HashMap<Integer, Map<String, Collection<Accession>>>();
    private static Map<Integer, Map<Edition, Double>> speciesAverages = new HashMap<Integer, Map<Edition, Double>>();
    private Map<String, Integer> goSetSizes;

    /**
     * 
     */
    public Cache() {
        log.info( "Cache created" );
    }

    @PostConstruct
    public void init() {
        // You can do here your initialization thing based on managed properties, if necessary.
        log.info( "Cache init" );
        log.info( "Used Memory: " + ( Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() )
                / 1000000 + " MB" );

        // Obtain SpeciesDAO.
        SpeciesDAO speciesDAO = daoFactoryBean.getGotrack().getSpeciesDAO();
        log.info( "SpeciesDAO successfully obtained: " + speciesDAO );

        speciesList = speciesDAO.list();

        // Obtain CacheDAO.
        CacheDAO cacheDAO = daoFactoryBean.getGotrack().getCacheDAO();
        log.info( "CacheDAO successfully obtained: " + cacheDAO );

        goSetSizes = cacheDAO.getGOSizes( 7, 137, 1, false );

        log.info( "Used Memory: " + ( Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() )
                / 1000000 + " MB" );

        log.info( goSetSizes.size() );

        // currentEditions = cacheDAO.getCurrentEditions();
        // log.debug( "Current Editions Size: " + currentEditions.size() );

        allEditions = cacheDAO.getAllEditions();
        log.debug( "All Editions Size: " + allEditions.size() );

        for ( Integer species : allEditions.keySet() ) {
            Edition ed = allEditions.get( species ).getLast();
            log.debug( "Current edition for species_id (" + species + "): " + ed.getEdition() );
            currentEditions.put( species, ed );
        }

        log.info( "Loading accession to geneSymbol cache..." );
        for ( Species species : speciesList ) {
            Integer speciesId = species.getId();
            Edition currEd = currentEditions.get( speciesId );

            if ( currEd == null ) continue;
            log.debug( species.getCommonName() + ": " + currEd.toString() );
            // Create Map of current accessions
            Map<String, Accession> currAccMap = cacheDAO.getCurrentAccessions( speciesId, currEd.getEdition() );
            currrentAccessions.put( speciesId, currAccMap );

            // Create symbols to collection of associated current accessions map
            Map<String, Collection<Accession>> currSymMap = new HashMap<String, Collection<Accession>>();
            for ( Accession acc : currAccMap.values() ) {
                Collection<Accession> symbolAccessions = currSymMap.get( acc.getSymbol() );
                if ( symbolAccessions == null ) {
                    symbolAccessions = new HashSet<Accession>();
                    symbolAccessions.add( acc );
                    currSymMap.put( acc.getSymbol(), symbolAccessions );
                } else {
                    symbolAccessions.add( acc );
                }

            }
            symbolToCurrentAccessions.put( speciesId, currSymMap );

            // symbols.put( speciesId, cacheDAO.getUniqueGeneSymbols( speciesId, currEd.getEdition() ) );

            log.info( "Done loading accession to geneSymbol for species (" + speciesId + "), size: "
                    + currrentAccessions.get( speciesId ).size() + " unique symbols: "
                    + symbolToCurrentAccessions.get( speciesId ).size() );
        }
        log.info( "Done loading accession to geneSymbol cache..." );

        speciesAverages = cacheDAO.getSpeciesAverages();
        log.info( "Used Memory: " + ( Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() )
                / 1000000 + " MB" );

    }

    public List<String> complete( String query, Integer species ) {

        if ( query == null ) return new ArrayList<String>();
        String queryUpper = query.toUpperCase();
        Collection<String> exact = new HashSet<String>();
        Collection<String> exactSynonym = new HashSet<String>();
        Collection<String> possible = new HashSet<String>();
        // Map<GOTerm, Long> results = new HashMap<GOTerm, Long>();
        // log.info( "search: " + queryString );
        Map<String, Accession> gs = currrentAccessions.get( species );
        if ( gs != null ) {
            for ( Accession gene : gs.values() ) {
                if ( queryUpper.toUpperCase().equals( gene.getSymbol().toUpperCase() ) ) {
                    exact.add( gene.getSymbol() );
                    continue;
                }
                List<String> synonyms = gene.getSynonyms();

                for ( String symbol : synonyms ) {
                    if ( queryUpper.equals( symbol.toUpperCase() ) ) {
                        exactSynonym.add( gene.getSymbol() );
                        continue;
                    }
                }

                String pattern = "(.*)" + queryUpper + "(.*)";
                // Pattern r = Pattern.compile(pattern);
                String m = gene.getSymbol().toUpperCase();
                // Matcher m = r.matcher( term.getTerm() );
                if ( m.matches( pattern ) ) {
                    possible.add( gene.getSymbol() );
                    continue;
                }
            }
        }

        if ( exact.size() > 0 ) {
            return new ArrayList<String>( exact );
        } else if ( exactSynonym.size() > 0 ) {
            return new ArrayList<String>( exactSynonym );
        } else if ( possible.size() > 0 ) {
            ArrayList<String> p = new ArrayList<String>( possible );
            Collections.sort( p, new LevenshteinComparator( query ) );
            return p;
        } else {
            return new ArrayList<String>();
        }

    }

    public List<Species> getSpeciesList() {
        return speciesList;
    }

    public Map<Integer, Edition> getCurrentEditions() {
        return currentEditions;
    }

    public Map<Integer, LinkedList<Edition>> getAllEditions() {
        return allEditions;
    }

    public Map<Integer, Map<String, Accession>> getCurrrentAccessions() {
        return currrentAccessions;
    }

    public Map<Integer, Map<String, Collection<Accession>>> getSymbolToCurrentAccessions() {
        return symbolToCurrentAccessions;
    }

    public Map<Integer, Map<Edition, Double>> getSpeciesAverages() {
        return speciesAverages;
    }

    public Map<String, Integer> getGoSetSizes() {
        return Collections.unmodifiableMap( goSetSizes );
    }

    public void setDaoFactoryBean( DAOFactoryBean daoFactoryBean ) {
        this.daoFactoryBean = daoFactoryBean;
    }

    public void setSettingsCache( SettingsCache settingsCache ) {
        this.settingsCache = settingsCache;
    }

}

class LevenshteinComparator implements Comparator<String> {

    private String compareTo;

    public LevenshteinComparator( String compareTo ) {
        super();
        this.compareTo = compareTo;
    }

    @Override
    public int compare( String a, String b ) {
        int d1 = StringUtils.getLevenshteinDistance( a, compareTo );
        int d2 = StringUtils.getLevenshteinDistance( b, compareTo );
        return d1 < d2 ? -1 : d1 == d2 ? 0 : 1;
    }
}
