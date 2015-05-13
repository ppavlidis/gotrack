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

package ubc.pavlab.gotrack.model.chart;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
public class ChartValues {

    Set<Series> series = new HashSet<>();

    public ChartValues() {
        super();
    }

    public boolean addSeries( Series s ) {
        return series.add( s );
    }

    public Series addSeries( String name, List<Point> ps ) {
        Series s = new Series( name );
        s.addDataPoint( ps );
        series.add( s );
        return s;
    }

    public Set<Series> getSeries() {
        return series;
    }

}
