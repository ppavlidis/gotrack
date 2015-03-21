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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;

import org.apache.log4j.Logger;
import org.primefaces.event.ItemSelectEvent;
import org.primefaces.extensions.model.timeline.TimelineEvent;
import org.primefaces.extensions.model.timeline.TimelineGroup;
import org.primefaces.extensions.model.timeline.TimelineModel;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.DateAxis;
import org.primefaces.model.chart.LineChartModel;
import org.primefaces.model.chart.LineChartSeries;

import ubc.pavlab.gotrack.dao.AnnotationDAO;
import ubc.pavlab.gotrack.dao.CacheDAO;
import ubc.pavlab.gotrack.exception.GeneNotFoundException;
import ubc.pavlab.gotrack.model.Accession;
import ubc.pavlab.gotrack.model.CustomTimelineModel;
import ubc.pavlab.gotrack.model.Edition;
import ubc.pavlab.gotrack.model.EvidenceReference;
import ubc.pavlab.gotrack.model.Gene;
import ubc.pavlab.gotrack.model.GeneOntologyTerm;
import ubc.pavlab.gotrack.model.GoChart;
import ubc.pavlab.gotrack.model.GraphTypeKey;
import ubc.pavlab.gotrack.model.GraphTypeKey.GraphType;
import ubc.pavlab.gotrack.model.Species;
import ubc.pavlab.gotrack.utilities.Jaccard;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@ManagedBean
@ViewScoped
public class TrackView {

    @ManagedProperty("#{cache}")
    private Cache cache;

    @ManagedProperty("#{stats}")
    private Stats stats;

    @ManagedProperty("#{sessionCache}")
    private SessionCache sessionCache;

    @ManagedProperty("#{daoFactoryBean}")
    private DAOFactoryBean daoFactoryBean;

    private static final Logger log = Logger.getLogger( TrackView.class );

    // DAO
    private AnnotationDAO annotationDAO;

    // Query params
    private Integer currentSpeciesId;
    private Species currentSpecies;
    private String query;

    // View static data
    private Gene currentGene;
    private Edition currentEdition;
    private List<Edition> allEditions = new ArrayList<>();
    private Map<GeneOntologyTerm, Set<EvidenceReference>> allAnnotations = new HashMap<>();

    // All charts
    private Map<GraphTypeKey, LineChartModel> lineChartModelMap = new HashMap<>();
    private Map<GraphTypeKey, GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> goChartMap = new HashMap<>();

    /* Current Chart Stuff */
    private LineChartModel currentChart; // Current chart
    private GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> currentGoChart;

    // Select Data Point functionality
    private String selectedDate;
    private Collection<GeneOntologyTerm> itemSelectTerms;
    private Collection<GeneOntologyTerm> filteredTerms;
    private List<GeneOntologyTerm> itemSelectViewTerms;
    private Number itemSelectValue;

    // Right Panel
    private List<GeneOntologyTerm> selectedTerms;
    private Collection<GeneOntologyTerm> filteredAllTerms;

    // Timeline
    private TimelineModel timelineModel;
    private List<CustomTimelineModel<GeneOntologyTerm>> timelines = new ArrayList<>();

    // Settings
    private boolean splitAccessions = true;
    private boolean propagate = false;
    private String graphType = "annotation";
    private String scale = "linear";
    private boolean chartsReady = false;
    // private boolean firstChartReady = false;

    // Static
    private static final List<String> aspects = Arrays.asList( "BP", "MF", "CC" );
    private static final List<String> graphs = Arrays.asList( "direct", "propagated" );
    private static final String COMBINED_TITLE = "All Accessions";

    public TrackView() {
        log.info( "TrackView created" );
        // System.gc();
        log.info( "Used Memory: " + ( Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() )
                / 1000000 + " MB" );
    }

    public String init() throws GeneNotFoundException {
        if ( FacesContext.getCurrentInstance().getPartialViewContext().isAjaxRequest() ) {
            return null; // Skip ajax requests.
        }
        log.info( "TrackView init: " + currentSpeciesId + ": " + query );
        currentGene = cache.getCurrentGene( currentSpeciesId, query );
        if ( currentGene == null ) {

            throw new GeneNotFoundException();
            /*
             * FacesContext facesContext = FacesContext.getCurrentInstance(); NavigationHandler navigationHandler =
             * facesContext.getApplication().getNavigationHandler(); navigationHandler.handleNavigation( facesContext,
             * null, "error400?faces-redirect=true" );
             */
        } else {
            // Get secondary accessions
            // Map<String, Collection<String>> primaryToSecondary = new HashMap<String, Collection<String>>();

            // Obtain AnnotationDAO.
            stats.countHit( query );
            log.info( "symbol: " + currentGene.getSymbol() );
            log.info( "synonyms: " + currentGene.getSynonyms() );
            log.info( "accessions: " + currentGene.getAccessions() );

            log.info( "Gene: " + currentGene );

            annotationDAO = daoFactoryBean.getGotrack().getAnnotationDAO();
            currentEdition = cache.getCurrentEditions( currentSpeciesId );
            // allEditions = cache.getAllEditions( currentSpeciesId );
            CacheDAO cacheDAO = daoFactoryBean.getGotrack().getCacheDAO();
            allEditions = cacheDAO.getAllEditions().get( currentSpeciesId );
            for ( Species s : cache.getSpeciesList() ) {
                if ( s.getId().equals( currentSpeciesId ) ) {
                    currentSpecies = s;
                }
            }

            return null;

        }

    }

    public void fetchAll() {
        log.info( "fetch Annotation Data" );
        // <PrimaryAccession, <Edition, <GOID, annotations>>>
        Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data = sessionCache
                .getData( currentGene );

        if ( data == null ) {

            data = annotationDAO.track3( currentSpeciesId, query, currentEdition.getEdition(),
                    currentEdition.getGoEditionId(), false );

            sessionCache.addData( currentGene, data );
            log.info( "Retrieved data from db" );
        } else {
            log.info( "Retrieved data from SessionCache" );

        }

        Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> propagatedData = propagate( data );
        log.info( "Annotation Data fetched" );
        createDirectCharts( data, "Direct Annotations vs Time", "Dates", "Direct Annotations" );
        createAllTerms( data );
        createPropagatedChart( propagatedData, "Propagated Annotations vs Time", "Dates", "Propagated Annotations" );
        createJaccardChart( data, "Jaccard Similarity vs Time", "Dates", "Jaccard Similarity" );
        createMultiChart( data, "Multifunctionality vs Time", "Dates", "Multifunctionality" );
        createLossGainChart( data, "Loss & Gain vs Time", "Dates", "Change" );
        sessionCache.addCharts( currentGene, lineChartModelMap, goChartMap );

        GraphTypeKey gtk = new GraphTypeKey( GraphType.annotation, true, false );

        currentGoChart = goChartMap.get( gtk );
        currentChart = lineChartModelMap.get( gtk );

        log.info( "size: " + currentGoChart );

        chartsReady = true;

    }

    private void createDirectCharts( Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data,
            String title, String xLabel, String yLabel ) {
        log.info( "Creating Direct Chart" );
        GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> goChart = new GoChart<>( title, xLabel, yLabel,
                data );

        Map<String, Map<Edition, Double>> staticData = new HashMap<>();
        staticData.put( "Species Avg", cache.getSpeciesAverage( currentSpeciesId ) );

        GraphTypeKey gtk = new GraphTypeKey( GraphType.annotation, true, false );

        // Base Chart
        initChart( gtk, goChart, new GoChart<Edition, Double>( title, xLabel, yLabel, staticData ) );

        // Combined Chart
        GraphTypeKey combinedGtk = new GraphTypeKey( GraphType.annotation, false, false );
        GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> combinedGOChart = new GoChart<>( title, xLabel,
                yLabel, combineDataByEdition( COMBINED_TITLE, data ) );
        initChart( combinedGtk, combinedGOChart, null );

        log.info( "Direct Chart Created" );
    }

    private void createAllTerms( Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data ) {
        log.info( "fetch All Terms" );
        allAnnotations.clear();

        for ( Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> series : data.values() ) {
            // We sort the editions so that newer versions will not be overwritten with older ones
            // This matters if we don't have go_term info for old editions
            List<Edition> eds = new ArrayList<Edition>( series.keySet() );
            Collections.sort( eds, Collections.reverseOrder() );
            for ( Edition e : eds ) {
                Map<GeneOntologyTerm, Set<EvidenceReference>> annots = series.get( e );
                for ( Entry<GeneOntologyTerm, Set<EvidenceReference>> goEntry : annots.entrySet() ) {
                    GeneOntologyTerm go = goEntry.getKey();
                    Set<EvidenceReference> evidence = goEntry.getValue();

                    Set<EvidenceReference> allEvidence = allAnnotations.get( go );
                    if ( allEvidence == null ) {
                        allEvidence = new HashSet<>();
                        allAnnotations.put( go, allEvidence );
                    }

                    allEvidence.addAll( evidence );

                }
            }
        }
        log.info( "All Terms Fetched" );

    }

    private Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> propagate(
            Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data ) {
        Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> propagatedData = new HashMap<>();

        for ( Entry<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> accessionEntry : data
                .entrySet() ) {
            String acc = accessionEntry.getKey();
            Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> propagatedSeries = new HashMap<>();
            propagatedData.put( acc, propagatedSeries );
            Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> series = accessionEntry.getValue();
            for ( Entry<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> editionEntry : series.entrySet() ) {
                Edition ed = editionEntry.getKey();
                Map<GeneOntologyTerm, Set<EvidenceReference>> propagatedAnnotations = cache.propagate(
                        editionEntry.getValue(), ed.getGoEditionId() );

                if ( propagatedAnnotations == null ) {
                    // No ontology exists for this edition
                } else {
                    propagatedSeries.put( ed, propagatedAnnotations );
                }

            }

        }
        return propagatedData;
    }

    private void createPropagatedChart(
            Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> propagatedData, String title,
            String xLabel, String yLabel ) {
        log.info( "Creating Propagated Chart" );

        // First we need to propagate the directly annotated GeneOntologyTerms

        GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> goChart = new GoChart<>( title, xLabel, yLabel,
                propagatedData );

        GraphTypeKey gtk = new GraphTypeKey( GraphType.annotation, true, true );

        // Base Chart
        initChart( gtk, goChart, new GoChart<Edition, Double>( title, xLabel, yLabel, null ) );

        // Combined Chart
        GraphTypeKey combinedGtk = new GraphTypeKey( GraphType.annotation, false, true );
        GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> combinedGOChart = new GoChart<>( title, xLabel,
                yLabel, combineDataByEdition( COMBINED_TITLE, propagatedData ) );
        initChart( combinedGtk, combinedGOChart, null );

        log.info( "Propagated Chart Created" );

    }

    private void createJaccardChart( Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data,
            String title, String xLabel, String yLabel ) {
        log.info( "Creating Jaccard Chart" );

        Map<String, Map<Edition, Double>> staticData = calculateJaccardData( data );

        GraphTypeKey gtk = new GraphTypeKey( GraphType.jaccard, true, false );
        GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> template = new GoChart<>( title, xLabel, yLabel );
        template.setMin( 0 );
        template.setMax( 1 );
        initChart( gtk, template, new GoChart<Edition, Double>( staticData ) );
        // initChart( gtk, new GoChart<Edition, Set<GeneOntologyTerm>>(), staticData );
        copyChart( gtk, new GraphTypeKey( GraphType.jaccard, false, false ) );
        copyChart( gtk, new GraphTypeKey( GraphType.jaccard, false, true ) );
        copyChart( gtk, new GraphTypeKey( GraphType.jaccard, true, true ) );

        log.info( "Jaccard Chart Created" );

    }

    private Map<String, Map<Edition, Double>> calculateJaccardData(
            Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data ) {
        Map<String, Map<Edition, Double>> staticData = new HashMap<>();

        for ( Entry<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> seriesEntry : data.entrySet() ) {
            String seriesAccession = seriesEntry.getKey();
            Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> series = seriesEntry.getValue();

            Set<GeneOntologyTerm> currentGoSet = series.get( currentEdition ).keySet();

            Map<Edition, Double> jaccardSeries = new HashMap<>();

            for ( Entry<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> editionEntry : series.entrySet() ) {
                Double jaccard = Jaccard.similarity( editionEntry.getValue().keySet(), currentGoSet );
                jaccardSeries.put( editionEntry.getKey(), jaccard );
            }

            staticData.put( seriesAccession, jaccardSeries );

        }
        return staticData;
    }

    private void createMultiChart( Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data,
            String title, String xLabel, String yLabel ) {
        log.info( "Creating Multifunctionality Chart" );

        GraphTypeKey gtk = new GraphTypeKey( GraphType.multifunctionality, true, false );
        GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> template = new GoChart<>( title, xLabel, yLabel );
        initChart( gtk, template, new GoChart<Edition, Double>( calculateMultiData( data ) ) );

        // Combined
        // staticData = createMultiData( goChartMap.get( new GraphTypeKey( GraphType.annotation, false, false ) ) );
        // gtk = new GraphTypeKey( GraphType.multifunctionality, false, false );
        // initChart( gtk, template, new GoChart<Edition, Double>( staticData ) );

        copyChart( gtk, new GraphTypeKey( GraphType.multifunctionality, false, false ) );
        copyChart( gtk, new GraphTypeKey( GraphType.multifunctionality, false, true ) );
        copyChart( gtk, new GraphTypeKey( GraphType.multifunctionality, true, true ) );
        log.info( "Multifunctionality Chart Created" );
    }

    private Map<String, Map<Edition, Double>> calculateMultiData(
            Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data ) {
        Map<String, Map<Edition, Double>> staticData = new HashMap<>();
        for ( Entry<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> seriesEntry : data.entrySet() ) {
            String seriesAccession = seriesEntry.getKey();
            Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> series = seriesEntry.getValue();

            Map<Edition, Double> multiSeries = new HashMap<>();

            for ( Entry<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> editionEntry : series.entrySet() ) {

                Double multi = 0.0;
                Edition ed = editionEntry.getKey();
                Integer total = cache.getAccessionSize( currentSpeciesId, ed );
                Set<GeneOntologyTerm> goSet = editionEntry.getValue().keySet();
                if ( total != null ) {
                    for ( GeneOntologyTerm term : goSet ) {
                        Integer inGroup = cache.getGoSetSizes( currentSpeciesId, ed.getEdition(), term.getGoId() );
                        if ( inGroup != null ) {
                            multi += 1.0 / ( inGroup * ( total - inGroup ) );
                        }
                    }

                    multiSeries.put( ed, multi );

                }

            }

            staticData.put( seriesAccession, multiSeries );

        }
        return staticData;
    }

    private void createLossGainChart( Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data,
            String title, String xLabel, String yLabel ) {
        log.info( "Creating Loss / Gain Chart" );

        Map<String, Map<Edition, Integer>> staticData = calculateLossGainData( data );

        GraphTypeKey gtk = new GraphTypeKey( GraphType.lossgain, false, false );
        GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> template = new GoChart<>( title, xLabel, yLabel );
        initChart( gtk, template, new GoChart<Edition, Integer>( staticData ) );
        // initChart( gtk, new GoChart<Edition, Set<GeneOntologyTerm>>(), staticData );
        copyChart( gtk, new GraphTypeKey( GraphType.lossgain, true, false ) );
        copyChart( gtk, new GraphTypeKey( GraphType.lossgain, false, true ) );
        copyChart( gtk, new GraphTypeKey( GraphType.lossgain, true, true ) );
        log.info( "Loss / Gain Chart Created" );

    }

    private Map<String, Map<Edition, Integer>> calculateLossGainData(
            Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data ) {
        Map<String, Map<Edition, Integer>> staticData = new HashMap<String, Map<Edition, Integer>>();

        for ( Entry<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> seriesEntry : data.entrySet() ) {
            String seriesAccession = seriesEntry.getKey();
            Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> series = seriesEntry.getValue();

            Map<Edition, Integer> lossSeries = new HashMap<Edition, Integer>();
            Map<Edition, Integer> gainSeries = new HashMap<Edition, Integer>();

            // Needs to sort as this calculation requires ordered iteration
            List<Edition> eds = new ArrayList<Edition>( series.keySet() );
            Collections.sort( eds );

            Set<GeneOntologyTerm> previousGoSet = null;
            for ( Edition e : eds ) {
                if ( previousGoSet != null ) {
                    lossSeries.put( e, setDifferenceSize( previousGoSet, series.get( e ).keySet() ) );
                    gainSeries.put( e, setDifferenceSize( series.get( e ).keySet(), previousGoSet ) );

                }
                previousGoSet = series.get( e ).keySet();
            }

            staticData.put( seriesAccession + " - loss", lossSeries );
            staticData.put( seriesAccession + " - gain", gainSeries );

        }
        return staticData;
    }

    private static <T> Integer setDifferenceSize( Set<T> setA, Set<T> setB ) {
        Set<T> tmp = new HashSet<T>( setA );
        tmp.removeAll( setB );
        return tmp.size();
    }

    private <T extends Number> void initChart( GraphTypeKey graphTypeKey,
            GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> goChart, GoChart<Edition, T> staticData ) {

        /* Base chart */
        lineChartModelMap.put( graphTypeKey, createChart( goChart, staticData ) );
        goChartMap.put( graphTypeKey, goChart );

        /*
         * Combined chart
         * 
         * GoChart<Edition, Set<GeneOntologyTerm>> g = GoChart.combineSeries( COMBINED_TITLE, goChart );
         * lineChartModelMap.put( graphTypeKey, createChart( g, null ) ); goChartMap.put( graphTypeKey, g );
         */
        log.info( graphTypeKey + " chart initialized" );
    }

    private <T extends Number> LineChartModel createChart(
            GoChart<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> goChart, GoChart<Edition, T> staticData ) {

        LineChartModel dateModel = new LineChartModel();

        if ( staticData != null ) {
            for ( Entry<String, LinkedHashMap<Edition, T>> es : staticData.getSeries().entrySet() ) {
                String label = es.getKey();
                Map<Edition, T> sData = es.getValue();

                LineChartSeries series = new LineChartSeries();
                series.setLabel( label );
                series.setShowMarker( false );

                for ( Entry<Edition, T> dataPoint : sData.entrySet() ) {
                    String date = dataPoint.getKey().getDate().toString();
                    T val = dataPoint.getValue();
                    series.set( date, val );
                }

                dateModel.addSeries( series );
            }
        }

        for ( Entry<String, LinkedHashMap<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> es : goChart
                .getSeries().entrySet() ) {
            String primary = es.getKey();
            Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> sData = es.getValue();

            LineChartSeries series = new LineChartSeries();
            series.setLabel( primary );
            series.setMarkerStyle( "filledDiamond" );

            for ( Entry<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> dataPoint : sData.entrySet() ) {
                String date = dataPoint.getKey().getDate().toString();
                Integer count = dataPoint.getValue().size();
                series.set( date, count );
            }

            dateModel.addSeries( series );
        }

        dateModel.setTitle( goChart.getTitle() );
        dateModel.setZoom( true );

        dateModel.setLegendPosition( "nw" );
        // dateModel.setAnimate( true );
        dateModel.setLegendRows( 8 );
        dateModel.setMouseoverHighlight( true );
        dateModel.setExtender( "chartExtender" );

        dateModel.getAxis( AxisType.Y ).setLabel( goChart.getyLabel() );

        if ( goChart.getMin() != null ) {
            dateModel.getAxis( AxisType.Y ).setMin( goChart.getMin() );
        }

        if ( goChart.getMax() != null ) {
            dateModel.getAxis( AxisType.Y ).setMax( goChart.getMax() );
        }

        DateAxis axis = new DateAxis( goChart.getxLabel() );
        // CategoryAxis axis = new CategoryAxis( "Editions" );
        axis.setTickAngle( -50 );
        // axis.setMax( currentEdition.getDate());
        axis.setTickFormat( "%b %#d, %y" );

        dateModel.getAxes().put( AxisType.X, axis );
        return dateModel;

    }

    private void copyChart( GraphTypeKey fromGraphTypeKey, GraphTypeKey toGraphTypeKey ) {
        lineChartModelMap.put( toGraphTypeKey, lineChartModelMap.get( fromGraphTypeKey ) );
        goChartMap.put( toGraphTypeKey, goChartMap.get( fromGraphTypeKey ) );
    }

    private static Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> combineDataByEdition(
            String seriesLabel, Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> data ) {
        Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> combinedSeries = new HashMap<>();
        for ( Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> s : data.values() ) {
            for ( Entry<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> editionEntry : s.entrySet() ) {
                Edition e = editionEntry.getKey();
                Map<GeneOntologyTerm, Set<EvidenceReference>> details = editionEntry.getValue();

                Map<GeneOntologyTerm, Set<EvidenceReference>> combinedDetails = combinedSeries.get( e );

                if ( combinedDetails == null ) {
                    combinedDetails = new HashMap<>();
                    combinedSeries.put( e, combinedDetails );
                }

                for ( Entry<GeneOntologyTerm, Set<EvidenceReference>> goEntry : details.entrySet() ) {
                    GeneOntologyTerm go = goEntry.getKey();
                    Set<EvidenceReference> evidence = combinedDetails.get( go );
                    if ( evidence == null ) {
                        evidence = new HashSet<>();
                        combinedDetails.put( go, evidence );
                    }
                    evidence.addAll( goEntry.getValue() );
                }

            }
        }

        Map<String, Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>>> combinedData = new HashMap<>();
        combinedData.put( seriesLabel, combinedSeries );

        return combinedData;
    }

    public void itemSelect( ItemSelectEvent event ) {

        // dateModel.getSeries().get( event.getSeriesIndex() ).getData().get( key );
        List<Entry<Object, Number>> es = new ArrayList<Entry<Object, Number>>( currentChart.getSeries()
                .get( event.getSeriesIndex() ).getData().entrySet() );

        // Keep in mind that the list of entry sets is in the order that the data was inserted, not the order it is
        // displayed!

        // log.info( "Key: " + es.get( event.getItemIndex() ).getKey() );
        // log.info( "Value: " + es.get( event.getItemIndex() ).getValue() );

        String date = ( String ) es.get( event.getItemIndex() ).getKey();
        itemSelectValue = es.get( event.getItemIndex() ).getValue();

        String label = currentChart.getSeries().get( event.getSeriesIndex() ).getLabel();

        selectedDate = date;

        Collection<Edition> ed = getGoEditionsFromDate( date );

        itemSelectTerms = new HashSet<>();
        if ( ed.size() == 0 ) {
            log.warn( "Found no editions for date (" + date + ")" );
        } else {

            if ( ed.size() > 1 ) log.warn( "Found more than one edition for date (" + date + ")" );

            Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> series = currentGoChart.get( label );

            if ( series == null ) {
                log.debug( "Could not find series for  (" + label + ")" );
            } else {
                itemSelectTerms = series.get( ed.iterator().next() ).keySet();
            }

        }

    }

    private Collection<Edition> getGoEditionsFromDate( Date date ) {
        return getGoEditionsFromDate( date.toString() );
    }

    private Collection<Edition> getGoEditionsFromDate( String date ) {
        List<Edition> results = new ArrayList<Edition>();
        for ( Edition ed : allEditions ) {
            if ( ed.getDate().toString().equals( date ) ) {
                results.add( ed );
            }

        }
        return results;
    }

    public void fetchTimelineFromPanel() {
        fetchTimeline( true );
    }

    /**
     * Entry-point for creating timeline from the dialog breakdown of an edition's go terms
     */
    public void fetchTimelineFromDialog() {
        fetchTimeline( false );
    }

    /**
     * @param fromPanel Fetch data then create timeline from panel or not (from dialog)
     */
    private void fetchTimeline( boolean fromPanel ) {

        // <Selected Term, <Date, Exists>>
        // Map<GeneOntologyTerm, Map<Date, Boolean>> timelineData = new HashMap<>();
        Map<GeneOntologyTerm, Map<Date, Set<String>>> timelineData = new HashMap<>();
        Map<GeneOntologyTerm, Set<String>> timelineGroups = new HashMap<>();

        Collection<GeneOntologyTerm> selected = fromPanel ? selectedTerms : itemSelectViewTerms;

        for ( GeneOntologyTerm term : selected ) {
            timelineData.put( term, new HashMap<Date, Set<String>>() );
            timelineGroups.put( term, new HashSet<String>() );
        }

        Map<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> allCombinedDirectSeries = goChartMap.get(
                new GraphTypeKey( GraphType.annotation, false, false ) ).get( COMBINED_TITLE );

        for ( Entry<Edition, Map<GeneOntologyTerm, Set<EvidenceReference>>> esSeries : allCombinedDirectSeries
                .entrySet() ) {
            Date date = esSeries.getKey().getDate();

            Map<GeneOntologyTerm, Set<EvidenceReference>> allTermsInEdition = esSeries.getValue();

            for ( GeneOntologyTerm geneOntologyTerm : selected ) {
                Set<String> categories = new HashSet<>();
                timelineData.get( geneOntologyTerm ).put( date, categories );
                Set<String> setGroups = timelineGroups.get( geneOntologyTerm );

                Set<EvidenceReference> allEvidenceInEdition = allTermsInEdition.get( geneOntologyTerm );

                if ( allEvidenceInEdition != null ) {

                    for ( EvidenceReference er : allEvidenceInEdition ) {
                        categories.add( er.getCategory() );
                        setGroups.add( er.getCategory() );
                    }
                }

            }

        }

        // timelineModel = createTimeline( timelineData );
        timelines = createTimelines( timelineData, timelineGroups );

    }

    /**
     * create timeline based on data
     * 
     * @param timelineData timeline data, map of terms to map of date to boolean representing whether the term existed
     *        on that date
     * @param timelineGroups
     * @return
     */
    private List<CustomTimelineModel<GeneOntologyTerm>> createTimelines(
            Map<GeneOntologyTerm, Map<Date, Set<String>>> timelineData,
            Map<GeneOntologyTerm, Set<String>> timelineGroups ) {
        List<CustomTimelineModel<GeneOntologyTerm>> timelines = new ArrayList<>();
        DateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
        Calendar cal = Calendar.getInstance();

        // Categories

        for ( Entry<GeneOntologyTerm, Map<Date, Set<String>>> esTermData : timelineData.entrySet() ) {

            GeneOntologyTerm term = esTermData.getKey();

            CustomTimelineModel<GeneOntologyTerm> model = new CustomTimelineModel<>( term );

            Map<Date, Set<String>> data = esTermData.getValue();
            Set<String> setGroups = timelineGroups.get( term );

            List<TimelineGroup> groups = new ArrayList<TimelineGroup>();
            List<TimelineEvent> events = new ArrayList<TimelineEvent>();

            for ( String grp : setGroups ) {
                groups.add( new TimelineGroup( grp, grp ) );
            }

            SortedSet<Date> dates = new TreeSet<Date>( data.keySet() );
            Date prevDate = null;
            for ( Date date : dates ) {
                if ( prevDate != null ) {

                    for ( String grp : setGroups ) {
                        boolean exists = data.get( prevDate ).contains( grp );
                        TimelineEvent event = new TimelineEvent( df.format( prevDate ), prevDate, date, false, grp,
                                exists ? "timeline-true timeline-hidden" : "timeline-false timeline-hidden" );
                        // model.add( event );
                        events.add( event );
                    }

                }

                prevDate = date;
            }

            // give the last edition a span of 1 month
            if ( dates.size() > 1 ) {
                for ( String grp : setGroups ) {
                    boolean exists = data.get( prevDate ).contains( grp );
                    cal.setTime( prevDate );
                    cal.add( Calendar.MONTH, 1 );
                    TimelineEvent event = new TimelineEvent( df.format( prevDate ), prevDate, cal.getTime(), false,
                            grp, exists ? "timeline-true timeline-hidden" : "timeline-false timeline-hidden" );
                    // model.add( event );
                    events.add( event );
                }
            }

            model.setGroups( groups );
            model.addAll( events );

            timelines.add( model );

        }

        return timelines;

    }

    /**
     * create timeline based on data
     * 
     * @param timelineData timeline data, map of terms to map of date to boolean representing whether the term existed
     *        on that date
     * @return
     */
    private TimelineModel createTimeline( Map<GeneOntologyTerm, Map<Date, Boolean>> timelineData ) {

        TimelineModel model = new TimelineModel();
        DateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
        Calendar cal = Calendar.getInstance();

        List<TimelineGroup> groups = new ArrayList<TimelineGroup>();
        List<TimelineEvent> events = new ArrayList<TimelineEvent>();

        for ( Entry<GeneOntologyTerm, Map<Date, Boolean>> esTermData : timelineData.entrySet() ) {
            GeneOntologyTerm term = esTermData.getKey();

            Map<Date, Boolean> data = esTermData.getValue();

            TimelineGroup group = new TimelineGroup( term.getGoId(), term );
            groups.add( group );
            // model.addGroup( group );

            SortedSet<Date> dates = new TreeSet<Date>( data.keySet() );
            Date prevDate = null;
            for ( Date date : dates ) {
                if ( prevDate != null ) {
                    boolean exists = data.get( prevDate );
                    TimelineEvent event = new TimelineEvent( df.format( prevDate ), prevDate, date, false,
                            term.getGoId(), exists ? "timeline-true timeline-hidden" : "timeline-false timeline-hidden" );
                    // model.add( event );
                    events.add( event );

                }

                prevDate = date;
            }

            // give the last edition a span of 1 month
            if ( dates.size() > 1 ) {
                boolean exists = data.get( prevDate );
                cal.setTime( prevDate );
                cal.add( Calendar.MONTH, 1 );
                TimelineEvent event = new TimelineEvent( df.format( prevDate ), prevDate, cal.getTime(), false,
                        term.getGoId(), exists ? "timeline-true timeline-hidden" : "timeline-false timeline-hidden" );
                // model.add( event );
                events.add( event );
            }

        }

        model.setGroups( groups );
        model.addAll( events );

        return model;

    }

    /**
     * Change current graph
     * 
     * @param graphType graph type to change to
     */
    public void changeGraph( String graphType ) {
        // System.out.println( "New value: " + graphType );
        this.graphType = graphType;
        reloadGraph();
    }

    /**
     * Sets current graph and series data based on @graphType
     */
    public void reloadGraph() {

        if ( graphType == null || graphType.equals( "" ) ) graphType = "annotation";
        // graphType = propagate ? "propagated" : "direct";

        GraphTypeKey gtk = new GraphTypeKey( GraphType.valueOf( graphType ), splitAccessions, propagate );
        log.info( gtk );
        // log.info( graphType + ( splitAccessions ? "" : COMBINED_SUFFIX ) );

        currentChart = lineChartModelMap.get( gtk );
        currentGoChart = goChartMap.get( gtk );
    }

    public void cleanTimeline() {
        timelineModel = null;
    }

    public void cleanFilteredTerms() {
        filteredTerms = null;
    }

    public boolean isPropagate() {
        return propagate;
    }

    public void setPropagate( boolean propagate ) {
        this.propagate = propagate;
    }

    public String getScale() {
        return scale;
    }

    public void setScale( String scale ) {
        this.scale = scale;
    }

    public List<String> getAspects() {
        return aspects;
    }

    public LineChartModel getCurrentChart() {
        return currentChart;
    }

    public ArrayList<Accession> getCurrentPrimaryAccessionsValues() {
        return new ArrayList<Accession>( currentGene.getAccessions() );
    }

    public Species getCurrentSpecies() {
        return currentSpecies;
    }

    public Gene getCurrentGene() {
        return currentGene;
    }

    public Integer getCurrentSpeciesId() {
        return currentSpeciesId;
    }

    public Collection<GeneOntologyTerm> getFilteredTerms() {
        return filteredTerms;
    }

    public Collection<GeneOntologyTerm> getFilteredAllTerms() {
        return filteredAllTerms;
    }

    public List<String> getGraphs() {
        return graphs;
    }

    // public List<TrackValue> getTrackValues() {
    // return trackValues;
    // }

    public String getQuery() {
        return query;
    }

    public String getSelectedDate() {
        return selectedDate;
    }

    public Collection<GeneOntologyTerm> getItemSelectTerms() {
        return itemSelectTerms;
    }

    public Collection<GeneOntologyTerm> getAllTerms() {
        return allAnnotations.keySet();
    }

    public List<GeneOntologyTerm> getSelectedTerms() {
        return selectedTerms;
    }

    public TimelineModel getTimelineModel() {
        return timelineModel;
    }

    public String getGraphType() {
        return this.graphType;
    }

    public Number getItemSelectValue() {
        return itemSelectValue;
    }

    public List<CustomTimelineModel<GeneOntologyTerm>> getTimelines() {
        return timelines;
    }

    public boolean isSplitAccessions() {
        return splitAccessions;
    }

    public void setSelectedTerms( List<GeneOntologyTerm> selectedTerms ) {
        this.selectedTerms = selectedTerms;
    }

    public void setCache( Cache cache ) {
        this.cache = cache;
    }

    public void setCurrentSpeciesId( Integer currentSpeciesId ) {
        this.currentSpeciesId = currentSpeciesId;
    }

    public void setDaoFactoryBean( DAOFactoryBean daoFactoryBean ) {
        this.daoFactoryBean = daoFactoryBean;
    }

    public void setFilteredTerms( Collection<GeneOntologyTerm> filteredTerms ) {
        this.filteredTerms = filteredTerms;
    }

    public void setFilteredAllTerms( Collection<GeneOntologyTerm> filteredAllTerms ) {
        this.filteredAllTerms = filteredAllTerms;
    }

    public void setQuery( String query ) {
        this.query = query;
    }

    public void setSelectedDate( String selectedDate ) {
        this.selectedDate = selectedDate;
    }

    public void setSplitAccessions( boolean splitAccessions ) {
        this.splitAccessions = splitAccessions;
    }

    public void setGraphType( String graphType ) {
        this.graphType = graphType;
    }

    public List<GeneOntologyTerm> getItemSelectViewTerms() {
        return itemSelectViewTerms;
    }

    public void setItemSelectViewTerms( List<GeneOntologyTerm> itemSelectViewTerms ) {
        this.itemSelectViewTerms = itemSelectViewTerms;
    }

    public boolean isChartsReady() {
        return chartsReady;
    }

    public void setStats( Stats stats ) {
        this.stats = stats;
    }

    public void setSessionCache( SessionCache sessionCache ) {
        this.sessionCache = sessionCache;
    }

}
