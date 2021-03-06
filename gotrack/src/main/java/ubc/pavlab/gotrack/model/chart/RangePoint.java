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

import java.sql.Date;

/**
 * Represents a long x and a range of y values (currently used for storing error bands)
 * 
 * @author mjacobson
 * @version $Id$
 */
public class RangePoint implements Point<Long, Range> {
    private final Long x;
    private final Range y;

    public RangePoint( long x, Number left, Number right ) {
        super();
        this.x = x;
        this.y = new Range( left, right );
    }

    public RangePoint( Date date, Number left, Number right ) {
        this.x = date.getTime();
        this.y = new Range( left, right );
    }

    @Override
    public Long getX() {
        return x;
    }

    @Override
    public Range getY() {
        return y;
    }

    @Override
    public int compareTo( Point<Long, Range> o ) {
        return this.x.compareTo( o.getX() );
    }
}