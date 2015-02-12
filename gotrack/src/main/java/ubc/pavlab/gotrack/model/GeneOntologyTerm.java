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

package ubc.pavlab.gotrack.model;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
public class GeneOntologyTerm {

    private String goId;
    private String name;
    private String aspect;
    private String evidence;
    private String reference;
    private Boolean obsolete;

    /**
     * 
     */
    public GeneOntologyTerm() {
        // TODO Auto-generated constructor stub
    }

    public GeneOntologyTerm( String goId, String evidence, String reference ) {
        super();
        this.goId = goId;
        this.evidence = evidence;
        this.reference = reference;
    }

    public GeneOntologyTerm( String goId, String evidence, String reference, String name, String aspect ) {
        super();
        this.goId = goId;
        this.evidence = evidence;
        this.reference = reference;
        this.name = name;
        this.aspect = aspect;
    }

    public String getGoId() {
        return goId;
    }

    public void setGoId( String goId ) {
        this.goId = goId;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence( String evidence ) {
        this.evidence = evidence;
    }

    public String getReference() {
        return reference;
    }

    public void setReference( String reference ) {
        this.reference = reference;
    }

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public String getAspect() {
        return aspect;
    }

    public void setAspect( String aspect ) {
        this.aspect = aspect;
    }

    public Boolean isObsolete() {
        return obsolete;
    }

    public Boolean getObsolete() {
        return obsolete;
    }

    public void setObsolete( Boolean obsolete ) {
        this.obsolete = obsolete;
    }

    @Override
    public String toString() {
        return "GeneOntologyTerm [goId=" + goId + ", name=" + name + ", aspect=" + aspect + ", evidence=" + evidence
                + ", reference=" + reference + ", obsolete=" + obsolete + "]";
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

}
