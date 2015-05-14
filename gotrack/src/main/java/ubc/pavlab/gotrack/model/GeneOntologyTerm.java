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

/**
 * The instances of this class may be shared between threads (sessions) and lazy-loaded at any time hence the
 * synchronization
 * 
 * @author mjacobson
 * @version $Id$
 */
public class GeneOntologyTerm implements Comparable<GeneOntologyTerm> {

    private final String goId;
    private String name;
    private String aspect;
    private boolean obsolete = false;

    public GeneOntologyTerm( String goId ) {
        super();
        this.goId = goId;
        this.name = null;
        this.aspect = null;
    }

    public GeneOntologyTerm( String goId, String name, String aspect ) {
        super();
        this.goId = goId;
        this.name = name;
        this.aspect = aspect;
    }

    public String getGoId() {
        // No need to synchronize as the field is final
        return goId;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized String getAspect() {
        return aspect;
    }

    public synchronized void setName( String name ) {
        this.name = name;
    }

    public synchronized void setAspect( String aspect ) {
        this.aspect = aspect;
    }

    public synchronized void setObsolete( boolean obsolete ) {
        this.obsolete = obsolete;
    }

    @Override
    public String toString() {
        return "GeneOntologyTerm [goId=" + goId + ", name=" + name + ", aspect=" + aspect + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( goId == null ) ? 0 : goId.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( getClass() != obj.getClass() ) return false;
        GeneOntologyTerm other = ( GeneOntologyTerm ) obj;
        if ( goId == null ) {
            if ( other.goId != null ) return false;
        } else if ( !goId.equals( other.goId ) ) return false;
        return true;
    }

    public synchronized boolean isObsolete() {
        return obsolete;
    }

    @Override
    public int compareTo( GeneOntologyTerm o ) {
        // TODO Auto-generated method stub
        return this.getGoId().compareTo( o.getGoId() );
    }

}
