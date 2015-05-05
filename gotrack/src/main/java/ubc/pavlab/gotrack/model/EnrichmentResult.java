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

import org.apache.commons.math3.distribution.HypergeometricDistribution;

/**
 * Immutable result of GO enrichment analysis for a single term
 * 
 * @author mjacobson
 * @version $Id$
 */
public class EnrichmentResult {

    private final double pvalue;
    private final int sampleAnnotated;
    private final int populationAnnotated;
    private final int sampleSize;
    private final int populationSize;

    public EnrichmentResult( double pvalue, int sampleAnnotated, int populationAnnotated, int sampleSize,
            int populationSize ) {
        super();
        this.pvalue = pvalue;
        this.sampleAnnotated = sampleAnnotated;
        this.populationAnnotated = populationAnnotated;
        this.sampleSize = sampleSize;
        this.populationSize = populationSize;
    }

    public EnrichmentResult( int sampleAnnotated, int populationAnnotated, int sampleSize, int populationSize ) {
        super();
        this.sampleAnnotated = sampleAnnotated;
        this.populationAnnotated = populationAnnotated;
        this.sampleSize = sampleSize;
        this.populationSize = populationSize;

        HypergeometricDistribution hyper = new HypergeometricDistribution( populationSize, populationAnnotated,
                sampleSize );
        this.pvalue = hyper.upperCumulativeProbability( sampleAnnotated );

    }

    /**
     * @param sampleAnnotated
     * @param populationAnnotated
     * @param sampleSize
     * @param populationSize
     * @param testSetSize Used for applying Bonferroni Multiple Tests Correction
     */
    public EnrichmentResult( int sampleAnnotated, int populationAnnotated, int sampleSize, int populationSize,
            int testSetSize ) {
        super();
        this.sampleAnnotated = sampleAnnotated;
        this.populationAnnotated = populationAnnotated;
        this.sampleSize = sampleSize;
        this.populationSize = populationSize;

        HypergeometricDistribution hyper = new HypergeometricDistribution( populationSize, populationAnnotated,
                sampleSize );
        this.pvalue = Math.min( hyper.upperCumulativeProbability( sampleAnnotated ) * testSetSize, 1 );

    }

    public double getExpected() {
        return sampleSize * ( ( double ) populationAnnotated ) / populationSize;
    }

    public double getPvalue() {
        return pvalue;
    }

    public int getSampleAnnotated() {
        return sampleAnnotated;
    }

    public int getPopulationAnnotated() {
        return populationAnnotated;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public int getPopulationSize() {
        return populationSize;
    }

}