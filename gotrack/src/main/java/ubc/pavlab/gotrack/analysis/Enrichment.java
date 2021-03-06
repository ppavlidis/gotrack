/*
 * The gotrack project
 * 
 * Copyright (c) 2016 University of British Columbia
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

package ubc.pavlab.gotrack.analysis;

import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import lombok.Getter;
import org.apache.log4j.Logger;
import ubc.pavlab.gotrack.model.hashkey.HyperUCFKey;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Runs over-representation analysis (enrichment) on given sample and population data.
 * 
 * @author mjacobson
 * @version $Id$
 */
@Getter
public class Enrichment<T, G> {

    private static final Logger log = Logger.getLogger( Enrichment.class );

    private CompletePopulation<T, G> samplePopulation;

    private boolean complete = false;

    private MultipleTestCorrection multipleTestCorrectionMethod = MultipleTestCorrection.BONFERRONI;

    private double threshold = 0.05;

    private int populationMin = 0;

    private int populationMax = 0;

    // Computed
    private Map<T, EnrichmentResult> results;

    private double cutoff;

    private Set<T> significantTerms;

    private Set<T> rejectedTerms;

    private int calculations;
    private int countTestedTerms;

    public Enrichment() {

    }

    public Enrichment( MultipleTestCorrection multipleTestCorrectionMethod, double threshold, int populationMin,
            int populationMax ) {
        this.multipleTestCorrectionMethod = multipleTestCorrectionMethod;
        this.threshold = threshold;
        this.populationMin = populationMin;
        this.populationMax = populationMax <= 0 ? Integer.MAX_VALUE : populationMax;
    }

    protected boolean runAnalysis( CompletePopulation<T, G> sample, Population<T> population,
            TObjectDoubleHashMap<HyperUCFKey> logProbCache ) {
        return runAnalysis( sample, population, sample.getProperties(), logProbCache );
    }

    public boolean runAnalysis( CompletePopulation<T, G> sample, Population<T> population ) {
        return runAnalysis( sample, population, sample.getProperties() );
    }

    public boolean runAnalysis( CompletePopulation<T, G> sample, Population<T> population, Set<T> tests ) {
        return runAnalysis( sample, population, tests, null );
    }

    protected boolean runAnalysis( CompletePopulation<T, G> sample, Population<T> population, Set<T> tests,
            TObjectDoubleHashMap<HyperUCFKey> logProbCache ) {

        if ( logProbCache == null ) {
            // Log Probability Memoization Cache
            logProbCache = new TObjectDoubleHashMap<>();
        }

        // Used to test for no entry in memoization cache
        double noEntryValue = logProbCache.getNoEntryValue();

        this.samplePopulation = sample;
        this.results = Maps.newHashMap();
        this.cutoff = 0;
        this.significantTerms = Sets.newHashSet();
        this.rejectedTerms = Sets.newHashSet();
        this.calculations = 0;

        int sampleSize = sample.size();
        int populationSize = population.size();

        Map<T, EnrichmentResult> results = Maps.newHashMap();
        Set<T> tested = Sets.newHashSet();
        Set<T> rejected = Sets.newHashSet(); // Rejected Terms
        Set<T> sig = Sets.newHashSet(); // Significant Terms

        for ( T t : tests ) {
            Integer sampleAnnotated = sample.countProperty( t );
            Integer populationAnnotated = population.countProperty( t );

            // check population requirements
            if ( sampleAnnotated == null || populationAnnotated == null || populationAnnotated < populationMin
                    || populationAnnotated > populationMax ) {
                rejected.add( t );
            } else {
                tested.add( t );

                // Combined map key
                HyperUCFKey key = new HyperUCFKey( sampleAnnotated, populationAnnotated, sampleSize,
                        populationSize );

                // Get log probability from memoization cache or compute it
                double p = logProbCache.get( key );
                if ( p == noEntryValue ) {
                    p = fishersExactTest( sampleAnnotated, populationAnnotated, sampleSize, populationSize );
                    // Cache result
                    logProbCache.put( key, p );
                }

                results.put( t,
                        new EnrichmentResult( p, sampleAnnotated, populationAnnotated, sampleSize, populationSize ) );

                calculations++;

            }

        }

        // Number of statistical tests done in analysis
        int testSetSize = tested.size();

        if ( testSetSize == 0 ) {
            // TODO
            log.info( "All tests rejected." );
            return false;
        }

        // Holds terms sorted by p-value
        LinkedHashSet<T> termsSortedByRank = results.entrySet().stream()
                .sorted( Comparator.comparingDouble( c -> c.getValue().getPvalue() ) )
                .map( Entry::getKey )
                .collect( Collectors.toCollection( LinkedHashSet::new ) );

        // We keep track of the cutoff p-value, set to some useful initial value
        double cutoff;
        if ( multipleTestCorrectionMethod.equals( MultipleTestCorrection.BH ) ) {
            // set lowest pvalue as cutoff for now;
            cutoff = results.get( termsSortedByRank.iterator().next() ).getPvalue();
        } else {
            cutoff = threshold;
        }

        // Containers
        int rank = -1;
        int k = 0;
        EnrichmentResult previousResult = null;
        Set<T> maybePile = Sets.newHashSet();
        Map<Integer, List<T>> standardRanks = Maps.newHashMap();

        /*
         * Loops through each term in order of p-value and compute rank, significance based on chosen method
         */
        for ( T term : termsSortedByRank ) {
            k++;
            EnrichmentResult er = results.get( term );
            if ( multipleTestCorrectionMethod.equals( MultipleTestCorrection.BONFERRONI ) ) {
                if ( er.getPvalue() * testSetSize <= threshold ) { // Check bonferroni correction against threshold
                    sig.add( term );
                }

            } else if ( multipleTestCorrectionMethod.equals( MultipleTestCorrection.BH ) ) {
                // Single pass method of BH step-up
                double qTresh = ( k ) * threshold / testSetSize;
                if ( er.getPvalue() <= qTresh ) {
                    // add this term and all terms in maybe pile
                    cutoff = er.getPvalue(); //update cutoff
                    sig.add( term );
                    if ( !maybePile.isEmpty() ) {
                        sig.addAll( maybePile );
                        maybePile.clear();
                    }
                } else {
                    // add this term to the maybe pile
                    maybePile.add( term );
                }
            } else {
                throw new RuntimeException( "This should never happen!" );
            }

            // ranks

            if ( !er.equals( previousResult ) ) {
                rank = k - 1;
            }
            List<T> termSet = standardRanks.get( rank );
            if ( termSet == null ) {
                termSet = Lists.newArrayList();
                standardRanks.put( rank, termSet );
            }
            termSet.add( term );
            er.setRank( rank );
            previousResult = er;
        }

        // Set significance
        for ( T t : sig ) {
            EnrichmentResult er = results.get( t );
            er.setSignificant( true );
        }

        // Compute fractional Ranks

        for ( Entry<Integer, List<T>> rankEntry : standardRanks.entrySet() ) {
            int standardRank = rankEntry.getKey();
            List<T> termSet = rankEntry.getValue();
            double newRank = standardRank + ( termSet.size() - 1 ) / 2.0;
            for ( T term : termSet ) {
                EnrichmentResult er = results.get( term );
                er.setFractionalRank( newRank );
            }
        }

        this.results = Collections.unmodifiableMap( results );
        this.cutoff = cutoff;
        this.significantTerms = Collections.unmodifiableSet( sig );
        this.rejectedTerms = Collections.unmodifiableSet( rejected );
        this.countTestedTerms = testSetSize;

        complete = true;
        return true;
    }

    private double fishersExactTest( int sampleAnnotated, int populationAnnotated, int sampleSize,
            int populationSize ) {
        return HyperGeometricUtilities.upperCumulativeProbabilityLogMethod( sampleAnnotated, populationAnnotated,
                sampleSize, populationSize );
    }

    void setThreshold( double t ) {
        threshold = t;
    }

    /**
     * @return unmodifiable map containing only significant results
     */
    public Map<T, EnrichmentResult> getSignificantResults() {
        return Maps.filterKeys( results, Predicates.in( significantTerms ) );
    }


    /**
     * @return Enrichment Result for specific term else null if doesn't exist
     */
    public EnrichmentResult getResult( T t ) {
        return results.get( t );
    }


    /**
     * @param n
     * @return Top N terms
     */
    public Set<T> getTopNTerms( int n ) {
        return significantTerms.stream().filter( t -> getResult( t ).getRank() < n ).collect( Collectors.toSet() );
    }

}
