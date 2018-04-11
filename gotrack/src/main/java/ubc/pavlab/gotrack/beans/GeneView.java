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

import com.google.common.base.Function;
import com.google.common.collect.*;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.primefaces.context.RequestContext;
import ubc.pavlab.gotrack.beans.service.AnnotationService;
import ubc.pavlab.gotrack.beans.service.MultifunctionalityService;
import ubc.pavlab.gotrack.beans.service.StatsService;
import ubc.pavlab.gotrack.exception.GeneNotFoundException;
import ubc.pavlab.gotrack.model.*;
import ubc.pavlab.gotrack.model.chart.ChartValues;
import ubc.pavlab.gotrack.model.chart.Series;
import ubc.pavlab.gotrack.model.chart.SeriesExtra;
import ubc.pavlab.gotrack.model.go.GeneOntologyTerm;
import ubc.pavlab.gotrack.model.table.GeneViewRightPanelRow;
import ubc.pavlab.gotrack.utilities.Jaccard;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.application.ProjectStage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Backing bean for the gene tracking functionality.
 *
 * @author mjacobson
 */
@Named
@ViewScoped
public class GeneView implements Serializable {

    private static final long serialVersionUID = -585608351928148001L;

    private static final Logger log = Logger.getLogger( GeneView.class );

    private static int MULTIFUNCTIONALITY_SCALE = 10000;

    @Inject
    private Cache cache;

    @Inject
    private StatsService statsService;

    @Inject
    private AnnotationService annotationService;

    @Inject
    private MultifunctionalityService multifunctionalityService;

    private String queryAccession;
    private Gene gene;

    // Data
    private Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> rawData;

    // Meta
    private Edition currentEdition;

    // Right Panel
    private List<GeneViewRightPanelRow> rightPanelTerms = Lists.newArrayList();
    private List<GeneViewRightPanelRow> rightPanelSelectedTerms;
    private Collection<GeneViewRightPanelRow> rightPanelFilteredTerms;
    private Edition rightPanelEdition;

    // Comparisons
    private List<Edition> comparisons = Lists.newArrayList();

    // Right Panel Click
//    private Collection<Annotation> rightPanelAnnotations = Sets.newHashSet();

    // View Annotations List
    private Collection<Annotation> viewAnnotations = new ArrayList<>();
    private Collection<Annotation> filteredViewAnnotations; // TODO use this
    private GeneOntologyTerm viewTerm;

    // Click event lists
    private Edition clickEdition;

    public GeneView() {
        log.info( "GeneView created" );
        log.info( "Used Memory: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000000
                + " MB" );
    }

    @PostConstruct
    public void postConstruct() {
    }

    /**
     * pre-render view
     * <p>
     * This is kept lightweight so that the page loads quickly and lazy loads the data using remote commands
     */
    public String init() throws GeneNotFoundException, IOException {
        if ( FacesContext.getCurrentInstance().getPartialViewContext().isAjaxRequest() ) {
            return null; // Skip ajax requests.
        }
        log.info( "GeneView init: " + queryAccession + " - " + gene );

        boolean isGeneSet = !StringUtils.isEmpty( queryAccession );

        if ( !isGeneSet ) {
            // no parameters; show search form
            gene = null;
            return null;
        }

        // display development message
        if ( FacesContext.getCurrentInstance().getApplication().getProjectStage() == ProjectStage.Development ) {
            FacesContext.getCurrentInstance().addMessage( "betaMessage", new FacesMessage( FacesMessage.SEVERITY_WARN,
                    "This is the DEVELOPMENT version of GOTrack!", "" ) );
        }

        gene = cache.getCurrentGene( queryAccession );

        if ( gene == null ) {
            // gene accession not found
            // Send to error page
            throw new GeneNotFoundException();
        }

        // Count gene hit
        statsService.countGeneHit( gene );
        log.info( "Gene: " + gene );

        currentEdition = cache.getCurrentEditions( gene.getSpecies() );
        rightPanelEdition = currentEdition;

        return null;

    }

    /**
     * Attempt to get data from cache, if not in cache get from DB.
     */
    private Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> retrieveData() {
        return retrieveData( Sets.<GeneOntologyTerm>newHashSet() );
    }

    /**
     * Attempt to get data from cache, if not in cache get from DB. Afterwards apply filter.
     */
    private Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> retrieveData(
            Collection<GeneViewRightPanelRow> filterTableValues ) {
        Set<GeneOntologyTerm> filterTerms = Sets.newHashSet();
        for ( GeneViewRightPanelRow tv : filterTableValues ) {
            filterTerms.add( tv.getTerm() );
        }
        return retrieveData( filterTerms );
    }

    /**
     * Attempt to get data from cache, if not in cache get from DB. Afterwards apply filter.
     */
    private Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> retrieveData(
            Set<GeneOntologyTerm> filterTerms ) {

        boolean bypassFilter = (filterTerms == null || filterTerms.size() == 0);

        Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> rawData = new HashMap<>();

        Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> cachedData = cache
                .getGeneData( gene );

        // Speedy return if no filters are required and data was cached
        if ( cachedData != null && bypassFilter ) {
            return cachedData;
        }

        Map<Edition, Map<GeneOntologyTerm, Set<Annotation>>> directRawData = null;
        Map<Edition, Map<GeneOntologyTerm, Set<Annotation>>> inferredRawData = null;

        // Get unfiltered data
        if ( cachedData == null ) {
            directRawData = annotationService.fetchTrackData( gene );
            inferredRawData = directRawData.entrySet().stream()
                    .collect( Collectors.toMap(
                            Entry::getKey,
                            e -> GeneOntologyTerm.propagateAnnotations( e.getValue().entrySet().stream() )
                    ) );
        } else {
            directRawData = cachedData.get( AnnotationType.D ).rowMap();
            inferredRawData = cachedData.get( AnnotationType.I ).rowMap();
        }

        ImmutableTable.Builder<Edition, GeneOntologyTerm, Set<Annotation>> rawdataBuilder = new ImmutableTable.Builder<Edition, GeneOntologyTerm, Set<Annotation>>();
        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : directRawData.entrySet() ) {
            Edition ed = entry.getKey();
            for ( Entry<GeneOntologyTerm, Set<Annotation>> entry2 : entry.getValue().entrySet() ) {
                GeneOntologyTerm t = entry2.getKey();
                if ( bypassFilter || filterTerms.contains( t ) ) {
                    rawdataBuilder.put( ed, t, entry2.getValue() );
                }

            }

        }
        rawdataBuilder.orderRowsBy( new Comparator<Edition>() {
            @Override
            public int compare( Edition o1, Edition o2 ) {
                return o1.compareTo( o2 );
            }
        } );

        rawData.put( AnnotationType.D, rawdataBuilder.build() );

        rawdataBuilder = new ImmutableTable.Builder<Edition, GeneOntologyTerm, Set<Annotation>>();
        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : inferredRawData.entrySet() ) {
            Edition ed = entry.getKey();
            for ( Entry<GeneOntologyTerm, Set<Annotation>> entry2 : entry.getValue().entrySet() ) {
                GeneOntologyTerm t = entry2.getKey();
                if ( bypassFilter || filterTerms.contains( t ) ) {
                    rawdataBuilder.put( ed, t, entry2.getValue() );
                }

            }

        }
        rawdataBuilder.orderRowsBy( new Comparator<Edition>() {
            @Override
            public int compare( Edition o1, Edition o2 ) {
                return o1.compareTo( o2 );
            }
        } );
        rawData.put( AnnotationType.I, rawdataBuilder.build() );

        // If no data existed in cache and we did not filter it then cache the data
        if ( cachedData == null && bypassFilter ) {
            cache.addGeneData( gene, rawData );
        }

        return rawData;

    }

    private List<GeneViewRightPanelRow> fetchRightPanelRows( Edition edition ) {
        return fetchRightPanelRows( edition, rawData.get( AnnotationType.I ).row( edition ).keySet() );
    }

    private List<GeneViewRightPanelRow> fetchRightPanelRows( Edition edition, Collection<GeneOntologyTerm> terms ) {
        List<GeneViewRightPanelRow> results = Lists.newArrayList();
        ImmutableMap<GeneOntologyTerm, Set<Annotation>> directs = rawData.get( AnnotationType.D ).row( edition );
        ImmutableMap<GeneOntologyTerm, Set<Annotation>> inferred = rawData.get( AnnotationType.I ).row( edition );
        for ( GeneOntologyTerm term : terms ) {
            Set<Annotation> annotations = inferred.get( term );
            if ( annotations != null ) {
                results.add( new GeneViewRightPanelRow( term,
                        directs.containsKey( term ) ? AnnotationType.D : AnnotationType.I, annotations ) );
            }
        }

        Collections.sort( results );
        return results;
    }

    private List<GeneViewRightPanelRow> fetchRightPanelRowsComparison( Edition editionA, Edition editionB ) {
        List<GeneViewRightPanelRow> results = Lists.newArrayList();

        ImmutableMap<GeneOntologyTerm, Set<Annotation>> inferredA = rawData.get( AnnotationType.I ).row( editionA );
        ImmutableMap<GeneOntologyTerm, Set<Annotation>> directsA = rawData.get( AnnotationType.D ).row( editionA );

        ImmutableMap<GeneOntologyTerm, Set<Annotation>> inferredB = rawData.get( AnnotationType.I ).row( editionB );
        ImmutableMap<GeneOntologyTerm, Set<Annotation>> directsB = rawData.get( AnnotationType.D ).row( editionB );

        for ( Entry<GeneOntologyTerm, Set<Annotation>> entry : inferredA.entrySet() ) {
            GeneOntologyTerm term = entry.getKey();
            Set<Annotation> annotations = entry.getValue();
            BitSet inSet = new BitSet();
            inSet.set( 0 );
            inSet.set( 1, inferredB.containsKey( term ) );
            results.add( new GeneViewRightPanelRow(
                    term,
                    directsA.containsKey( term ) ? AnnotationType.D : AnnotationType.I,
                    annotations,
                    inSet ) );

        }

        // Considered using Maps.difference. However, this method is not as efficient
        // as the view returned by Sets.difference
        BitSet inSet = new BitSet();
        inSet.set( 0, 0 );
        inSet.set( 1 );
        for ( GeneOntologyTerm term : Sets.difference( inferredB.keySet(), inferredA.keySet() ) ) {
            Set<Annotation> annotations = inferredB.get( term );
            results.add( new GeneViewRightPanelRow(
                    term,
                    directsB.containsKey( term ) ? AnnotationType.D : AnnotationType.I,
                    annotations, inSet ) );

        }

        comparisons = Lists.newArrayList( editionB );

        Collections.sort( results );
        return results;
    }


    private List<GeneViewRightPanelRow> addRightPanelRowsComparison( Edition newComparison ) {

//        if (rightPanelTerms == null || rightPanelTerms.isEmpty()) {
//            return fetchRightPanelRowsComparison( rightPanelEdition, newComparison );
//        }

        BitSet example = rightPanelTerms.iterator().next().getInSet();
        int nextBitIndex = comparisons.size() + 1;

        ImmutableMap<GeneOntologyTerm, Set<Annotation>> newInferred = rawData.get( AnnotationType.I ).row( newComparison );
        ImmutableMap<GeneOntologyTerm, Set<Annotation>> newDirect = rawData.get( AnnotationType.D ).row( newComparison );

        for ( GeneViewRightPanelRow rightPanelTerm : rightPanelTerms ) {
            BitSet inSet = rightPanelTerm.getInSet();
            inSet.set( nextBitIndex, newInferred.containsKey( rightPanelTerm.getTerm() ) );
        }


        // Not too fast but better than alternatives... probably
        Set<GeneOntologyTerm> termsInRightPanel = Sets.newHashSet( Collections2.transform( rightPanelTerms, new Function<GeneViewRightPanelRow, GeneOntologyTerm>() {
            @Override
            public GeneOntologyTerm apply( GeneViewRightPanelRow row ) {
                return row.getTerm();
            }
        } ) );

        BitSet inSet = new BitSet( nextBitIndex );
        inSet.set( nextBitIndex );
        for ( GeneOntologyTerm term : Sets.difference( newInferred.keySet(), termsInRightPanel ) ) {
            Set<Annotation> annotations = newInferred.get( term );
            rightPanelTerms.add( new GeneViewRightPanelRow(
                    term,
                    newDirect.containsKey( term ) ? AnnotationType.D : AnnotationType.I,
                    annotations,
                    inSet ) );

        }

        comparisons.add( newComparison );

        Collections.sort( rightPanelTerms );
        return rightPanelTerms;
    }

    /**
     * Entry point for initiating the retrieval of necessary data.
     */
    public void fetchData() {
        log.debug( "fetchData" );

        // retrieve data nad apply no filters
        rawData = retrieveData();

        // A map that will be needed in the front end for drilling down
        Map<Long, Integer> dateToEdition = new HashMap<>();

        for ( Edition ed : rawData.get( AnnotationType.I ).rowKeySet() ) {
            dateToEdition.put( ed.getDate().getTime(), ed.getEdition() );
        }

        RequestContext.getCurrentInstance().addCallbackParam( "dateToEdition", new Gson().toJson( dateToEdition ) );

        //allTerms = rawData.get( AnnotationType.DIRECT ).columnKeySet();
        rightPanelTerms = fetchRightPanelRows( rightPanelEdition );
//        Collections.sort( rightPanelTerms );

    }

    private Map<String, Object> createHCCallbackParamFail( String info ) {
        Map<String, Object> hcGsonMap = Maps.newHashMap();
        hcGsonMap.put( "success", false );
        hcGsonMap.put( "info", info );
        return hcGsonMap;
    }

    private Map<String, Object> createHCCallbackParamMap( ChartValues chart ) {
        Map<String, Object> hcGsonMap = Maps.newHashMap();
        hcGsonMap.put( "success", true );
        hcGsonMap.put( "chart", chart );
        return hcGsonMap;
    }

    /**
     * Create chart showing counts of unique terms annotated to this gene over time (both directly and through
     * propagation)
     *
     * @param rawData data
     */
    private void fetchAnnotationChart(
            Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> rawData ) {
        log.debug( "fetchAnnotationChart" );

        // Collect data from cache about species aggregates
        //        Map<Edition, Aggregate> aggregates = cache.getAggregates( species.getId() );

        ChartValues chart = new ChartValues( "Terms Annotated to " + gene.getSymbol(),
                "Annotations Count", "Date" );
        chart.setMin( 0 );

        //        // Create series for species aggregates
        //        Series aggregateSeries = new Series( "Species Direct Avg" );
        //        for ( Entry<Edition, Aggregate> entry : aggregates.entrySet() ) {
        //            aggregateSeries.addDataPoint( entry.getKey().getDate(), entry.getValue().getAvgDirectByGene() );
        //        }
        //        chart.addSeries( aggregateSeries );

        //Create series for direct annotations count
        Series directCountSeries = new Series( "Direct" );
        SeriesExtra aggregateSeries = new SeriesExtra( "Direct Species Mean" );
        aggregateSeries.putExtra( "color", "#939393" );
        SeriesExtra aggregateInferredSeries = new SeriesExtra( "Inferred Species Mean" );
        aggregateInferredSeries.putExtra( "color", "#939393" );
        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : rawData.get( AnnotationType.D )
                .rowMap().entrySet() ) {
            Edition ed = entry.getKey();
            int count = entry.getValue().size();
            directCountSeries.addDataPoint( ed.getDate(), count );

        }

        // Create series for inferred annotations count
        Series inferredCountSeries = new Series( "Inferred" );

        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : rawData.get( AnnotationType.I )
                .rowMap().entrySet() ) {
            Edition ed = entry.getKey();
            int count = entry.getValue().size();
            inferredCountSeries.addDataPoint( ed.getDate(), count );

            // Averages
            Aggregate agg = cache.getAggregate( ed );
            if ( agg != null ) {
                aggregateSeries.addDataPoint( ed.getDate(), agg.getAvgDirectByGene() );
                aggregateInferredSeries.addDataPoint( ed.getDate(), agg.getAvgInferredByGene() );
            }
        }

        chart.addSeries( aggregateSeries );
        chart.addSeries( aggregateInferredSeries );
        chart.addSeries( directCountSeries );
        chart.addSeries( inferredCountSeries );

        Map<String, Object> hcGsonMap = createHCCallbackParamMap( chart );

        RequestContext.getCurrentInstance().addCallbackParam( "HC", new Gson().toJson( hcGsonMap ) );
    }

    /**
     * Entry point for fetching the annotation chart
     */
    public void fetchAnnotationChart() {
        fetchAnnotationChart( rawData );

    }

    /**
     * Create chart showing similarity of terms annotated to this gene in an edition compared to the most current
     * edition (both directly and through propagation)
     *
     * @param rawData data
     */
    private void fetchJaccardChart(
            Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> rawData ) {
        log.debug( "fetchJaccardChart" );

        ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>> directData = rawData.get( AnnotationType.D );
        ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>> inferredData = rawData
                .get( AnnotationType.I );

        ChartValues chart = new ChartValues( "Similarity of " + gene.getSymbol(),
                "Jaccard Index", "Date" );
        chart.setMin( 0 );
        chart.setMax( 1 );

        // Create jaccard between edition and current edition
        Edition currentEdition = Collections.max( directData.rowKeySet() );

        // For direct annotations
        Series directSeries = new Series( "Direct" );
        SeriesExtra averageDirectSeries = new SeriesExtra( "Direct Species Mean" );
        averageDirectSeries.putExtra( "color", "#939393" );

        Set<GeneOntologyTerm> currentGOSet = directData.row( currentEdition ).keySet();
        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : directData.rowMap().entrySet() ) {
            Edition ed = entry.getKey();
            Double jaccard = Jaccard.similarity( entry.getValue().keySet(), currentGOSet );
            directSeries.addDataPoint( ed.getDate(), jaccard );
        }

        // For Inferred annotations
        Series inferredSeries = new Series( "Inferred" );
        SeriesExtra averageInferredSeries = new SeriesExtra( "Inferred Species Mean" );
        averageInferredSeries.putExtra( "color", "#939393" );

        currentGOSet = inferredData.row( currentEdition ).keySet();
        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : inferredData.rowMap().entrySet() ) {
            Edition ed = entry.getKey();
            Double jaccard = Jaccard.similarity( entry.getValue().keySet(), currentGOSet );
            inferredSeries.addDataPoint( ed.getDate(), jaccard );

            // Averages
            Aggregate agg = cache.getAggregate( ed );
            if ( agg != null ) {
                averageDirectSeries.addDataPoint( ed.getDate(), agg.getAvgDirectSimilarity() );
                averageInferredSeries.addDataPoint( ed.getDate(), agg.getAvgInferredSimilarity() );
            }
        }

        chart.addSeries( averageDirectSeries );
        chart.addSeries( averageInferredSeries );
        chart.addSeries( directSeries );
        chart.addSeries( inferredSeries );

        Map<String, Object> hcGsonMap = createHCCallbackParamMap( chart );

        RequestContext.getCurrentInstance().addCallbackParam( "HC", new Gson().toJson( hcGsonMap ) );
    }

    /**
     * Entry point for fetching the similarity chart
     */
    public void fetchJaccardChart() {
        fetchJaccardChart( rawData );

    }

    /**
     * Create chart showing multifunctionality of this gene over time (Gillis J, Pavlidis P (2011) The Impact of
     * Multifunctional Genes on "Guilt by Association" Analysis. PLoS ONE 6(2): e17258. doi:
     * 10.1371/journal.pone.0017258)
     *
     * @param rawData data
     */
    private void fetchMultifunctionalityChart(
            Map<AnnotationType, ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>>> rawData ) {
        log.debug( "fetchMultifunctionalityChart" );

        ChartValues chart = new ChartValues( "Multifunctionality of " + gene.getSymbol(), "Multifunctionality [10^-5]",
                "Date" );
        chart.setMin( 0 );

        // Calculate multifunctionality of the gene in each edition
        Series multiSeries = new Series( "Multifunctionality" );
        SeriesExtra averageSeries = new SeriesExtra( "Species Mean" );
        averageSeries.putExtra( "color", "#939393" );
        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : rawData.get( AnnotationType.I )
                .rowMap().entrySet() ) {
            Edition ed = entry.getKey();

            Double multi = multifunctionalityService.multifunctionality( entry.getValue().keySet(), ed );
            if ( multi != null ) {
                // Scaled by 10^4
                multiSeries.addDataPoint( ed.getDate(), MULTIFUNCTIONALITY_SCALE * multi );
            }

            // Averages
            Aggregate agg = cache.getAggregate( ed );
            if ( agg != null ) {
                // Scaled by 10^4
                averageSeries.addDataPoint( ed.getDate(), MULTIFUNCTIONALITY_SCALE * agg.getAvgMultifunctionality() );
            }
        }
        chart.addSeries( averageSeries );
        chart.addSeries( multiSeries );

        Map<String, Object> hcGsonMap = createHCCallbackParamMap( chart );

        RequestContext.getCurrentInstance().addCallbackParam( "HC", new Gson().toJson( hcGsonMap ) );
    }

    /**
     * Entry point for fetching the multifunctionality chart
     */
    public void fetchMultifunctionalityChart() {
        fetchMultifunctionalityChart( rawData );
    }

    /**
     * Entry point for fetching data to create a gantt chart of annotations categories over time
     */
    public void fetchTimeline() {
        log.debug( "fetchTimeline" );
        ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>> data;
        String subtitle;
        if ( rightPanelSelectedTerms == null || rightPanelSelectedTerms.size() == 0 ) {
            data = rawData.get( AnnotationType.I );
            subtitle = "All Terms";
        } else {
            data = retrieveData( rightPanelSelectedTerms ).get( AnnotationType.I );
            if (rightPanelSelectedTerms.size() > 3) {
                subtitle = rightPanelSelectedTerms.size() + " Terms";
            } else if (rightPanelSelectedTerms.size() == 1) {
                GeneOntologyTerm t = rightPanelSelectedTerms.iterator().next().getTerm();
                subtitle = t.getGoId() + " - " + t.getName();
            } else {
                subtitle =  rightPanelSelectedTerms.stream().map( t -> t.getTerm().getGoId() ).collect( Collectors.joining( ", " ) );
            }
        }

        ChartValues chart = new ChartValues( "Annotation Categories of Terms in " + gene.getSymbol(),
                "Counts", "Date" );
        chart.setSubtitle( subtitle );
        Map<String, Series> seriesMap = Maps.newHashMap();

        List<Edition> allEditions = new ArrayList<>( cache.getAllEditions( this.gene.getSpecies() ) );
        Collections.sort( allEditions );

        for ( Edition ed : allEditions ) {

            ImmutableMap<GeneOntologyTerm, Set<Annotation>> editionData = data.row( ed );

            // Group by annotation.evidence.category
            Map<String, Long> categoryCounts = editionData.entrySet().stream()
                    .flatMap( e -> e.getValue().stream() )
                    .collect( Collectors.groupingBy( o -> o.getEvidence().getCategory(), Collectors.counting() ) );
            for (String category : cache.getEvidenceCategories() ) {
                seriesMap.computeIfAbsent( category, Series::new ).addDataPoint( ed.getDate(), categoryCounts.getOrDefault( category, 0L ) );
            }
        }

        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> entry : data.rowMap().entrySet() ) {
            Edition ed = entry.getKey();


        }

        for ( Series series : seriesMap.values().stream().sorted( Comparator.comparing( Series::getName ) ).collect( Collectors.toList() ) ) {
            chart.addSeries( series );
        }

        Map<String, Object> hcGsonMap = createHCCallbackParamMap( chart );
        hcGsonMap.put( "categories", cache.getEvidenceCategories().stream().sorted().collect( Collectors.toList() ) );
        RequestContext.getCurrentInstance().addCallbackParam( "HC", new Gson().toJson( hcGsonMap ) );

    }

    public Collection<Annotation> fetchTermAnnotations( GeneOntologyTerm term ) {
        log.info( "fetchTermAnnotations" );
        return rawData.get( AnnotationType.I ).row( rightPanelEdition ).get( term );
    }


    /**
     * Click event functionality for annotation chart
     */
    public void fetchAnnotationPointData() {
        log.debug( "fetchAnnotationPointData" );
        Integer editionId;
        try {

            editionId = Integer.valueOf(
                    FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get( "edition" ) );
        } catch (NumberFormatException e) {
            log.error( e );
            return;
        }

        clickEdition = cache.getEdition( this.gene.getSpecies(), editionId );

        if ( clickEdition == null ) {
            log.warn( "Selected edition id has no corresponding edition object" );
            return;
        }

        try {
            rightPanelTerms = fetchRightPanelRows( clickEdition );
            rightPanelFilteredTerms = null;
            rightPanelSelectedTerms = null;
            rightPanelEdition = clickEdition;

            // Reset comparison fields
            comparisons = Lists.newArrayList();
        } catch (NullPointerException e) {
            log.error( e );
            return;
        }

    }

    /**
     * Ctrl-Click event functionality for annotation chart
     */
    public void fetchAnnotationComparisonData() {
        log.debug( "fetchAnnotationComparisonData" );
        Integer compareEditionId;
        try {
            compareEditionId = Integer.valueOf(
                    FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get( "compareEdition" ) );
        } catch (NumberFormatException e) {
            log.error( e );
            return;
        }

        Edition compareEdition = cache.getEdition( this.gene.getSpecies(), compareEditionId );

        if ( compareEdition == null ) {
            log.warn( "Selected compare edition id has no corresponding edition object" );
            return;
        }

        try {
            rightPanelTerms = fetchRightPanelRowsComparison( rightPanelEdition, compareEdition );
            rightPanelFilteredTerms = null;
            rightPanelSelectedTerms = null;
        } catch (NullPointerException e) {
            log.error( e );
            return;
        }

    }

    /**
     * Ctrl-Shift-Click event functionality for annotation chart
     */
    public void addAnnotationComparisonData() {
        log.debug( "addAnnotationComparisonData" );
        Integer compareEditionId;
        try {
            compareEditionId = Integer.valueOf(
                    FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get( "compareEdition" ) );
        } catch (NumberFormatException e) {
            log.error( e );
            return;
        }

        Edition compareEdition = cache.getEdition( this.gene.getSpecies(), compareEditionId );

        if ( compareEdition == null ) {
            log.warn( "Selected compare edition id has no corresponding edition object" );
            return;
        }

        try {
            addRightPanelRowsComparison( compareEdition );
            rightPanelFilteredTerms = null;
            rightPanelSelectedTerms = null;
        } catch (NullPointerException e) {
            log.error( e );
            return;
        }

    }

    /**
     * Click event functionality for clicking timeline gantt chart
     */
    public void fetchTimelinePointData() {
        Integer editionId;
        try {

            editionId = Integer.valueOf(
                    FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get( "edition" ) );
        } catch (NumberFormatException e) {
            log.error( e );
            return;
        }
        clickEdition = cache.getEdition( this.gene.getSpecies(), editionId );

        String goId = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get( "termId" );

        viewTerm = cache.getTerm( clickEdition, goId );

        viewAnnotations = rawData.get( AnnotationType.I ).get( clickEdition, viewTerm );
        filteredViewAnnotations = null;
    }

    public void fetchEditionsForSelectedTerms() {
        List<Long> missingEditionDates = Lists.newArrayList();
        ImmutableTable<Edition, GeneOntologyTerm, Set<Annotation>> inferred = rawData.get( AnnotationType.I );

        Collection<GeneOntologyTerm> terms = rightPanelSelectedTerms.stream().map( GeneViewRightPanelRow::getTerm ).collect( Collectors.toSet() );

        for ( Entry<Edition, Map<GeneOntologyTerm, Set<Annotation>>> editionMapEntry : inferred.rowMap().entrySet() ) {
            Edition edition = editionMapEntry.getKey();
            Boolean disjoint = Collections.disjoint( terms, editionMapEntry.getValue().keySet() );
            if ( disjoint ) {
                missingEditionDates.add( edition.getDate().getTime() );
            }
        }
        RequestContext.getCurrentInstance().addCallbackParam( "missing_editions", new Gson().toJson( missingEditionDates ) );
    }

    /**
     * custom filter function for primefaces data table column, filters by multiple booleans
     */
    public boolean filterByBitSet( Object value, Object filter, Locale locale ) {
        Set<String> filterIndices = (filter == null) ? null : Sets.newHashSet( (String[]) filter );
        if ( filterIndices == null || filterIndices.isEmpty() ) {
            return true;
        }

        if ( value == null ) {
            return false;
        }

        BitSet enabledBits = (BitSet) value;

        BitSet filterBits = new BitSet( enabledBits.length() );
        for ( String i : filterIndices ) {
            filterBits.set( Integer.valueOf( i ) );
        }

        return filterBits.equals( enabledBits );
    }

    // Getters & Setters

    public String getQueryAccession() {
        return queryAccession;
    }

    public void setQueryAccession( String queryAccession ) {
        this.queryAccession = queryAccession;
    }

    public Gene getGene() {
        return gene;
    }

    public void setGene( Gene gene ) {
        this.gene = gene;
    }

    public Collection<Annotation> getViewAnnotations() {
        return viewAnnotations;
    }

    public Collection<Annotation> getFilteredViewAnnotations() {
        return filteredViewAnnotations;
    }

    public GeneOntologyTerm getViewTerm() {
        return viewTerm;
    }

    public void setViewTerm( GeneOntologyTerm viewTerm ) {
        this.viewTerm = viewTerm;
    }

    public Edition getClickEdition() {
        return clickEdition;
    }

    public List<GeneViewRightPanelRow> getRightPanelTerms() {
        return rightPanelTerms;
    }

    public void setRightPanelTerms( List<GeneViewRightPanelRow> rightPanelTerms ) {
        this.rightPanelTerms = rightPanelTerms;
    }

    public List<GeneOntologyTerm> getRightPanelSelectedGeneOntologyTerms() {
        return rightPanelSelectedTerms.stream().map( GeneViewRightPanelRow::getTerm ).collect( Collectors.toList() );
    }
    public List<GeneViewRightPanelRow> getRightPanelSelectedTerms() {
        return rightPanelSelectedTerms;
    }

    public void setRightPanelSelectedTerms( List<GeneViewRightPanelRow> rightPanelSelectedTerms ) {
        this.rightPanelSelectedTerms = rightPanelSelectedTerms;
    }

    public Collection<GeneViewRightPanelRow> getRightPanelFilteredTerms() {
        return rightPanelFilteredTerms;
    }

    public void setRightPanelFilteredTerms( Collection<GeneViewRightPanelRow> rightPanelFilteredTerms ) {
        this.rightPanelFilteredTerms = rightPanelFilteredTerms;
    }

    public Edition getRightPanelEdition() {
        return rightPanelEdition;
    }

    public List<Edition> getComparisons() {
        return comparisons;
    }
}
