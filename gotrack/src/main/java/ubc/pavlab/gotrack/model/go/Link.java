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

package ubc.pavlab.gotrack.model.go;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
public class Link {
    private final Term link;
    private final RelationshipType type;

    public Term getLink() {
        return link;
    }

    public RelationshipType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Link [link=" + link + ", type=" + type + "]";
    }

    public Link( Term link, RelationshipType type ) {
        super();
        this.link = link;
        this.type = type;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( link == null ) ? 0 : link.hashCode() );
        result = prime * result + ( ( type == null ) ? 0 : type.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( getClass() != obj.getClass() ) return false;
        Link other = ( Link ) obj;
        if ( link == null ) {
            if ( other.link != null ) return false;
        } else if ( !link.equals( other.link ) ) return false;
        if ( type != other.type ) return false;
        return true;
    }

}