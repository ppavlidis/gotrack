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

import java.sql.Date;

import ubc.pavlab.gotrack.model.GOEdition;

/**
 * Data Transfer Object for {@link GOEdition}
 * 
 * @author mjacobson
 * @version $Id$
 */
public final class GOEditionDTO {
    private final Integer id;
    private final Date date;

    public GOEditionDTO( Integer id, Date date ) {
        super();
        this.id = id;
        this.date = date;
    }

    public Integer getId() {
        return id;
    }

    public Date getDate() {
        return date;
    }

}
