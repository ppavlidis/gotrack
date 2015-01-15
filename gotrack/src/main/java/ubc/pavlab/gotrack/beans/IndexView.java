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

package ubc.pavlab.gotrack.beans;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.event.ComponentSystemEvent;

import ubc.pavlab.gotrack.model.Accession;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@ManagedBean
@ViewScoped
public class IndexView implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -3038133837848883737L;
    private Integer currentSpecies;
    private String query;

    @ManagedProperty("#{cache}")
    private Cache cache;

    /**
     * private Integer currentSpecies; private String query; /**
     */
    public IndexView() {
        System.out.println( "IndexView created" );
    }

    public void validateQuery( ComponentSystemEvent event ) {

        FacesContext fc = FacesContext.getCurrentInstance();

        UIComponent components = event.getComponent();

        // get password
        UIInput uiInputSpecies = ( UIInput ) components.findComponent( "selectspecies" );
        Integer selectspecies = ( Integer ) uiInputSpecies.getLocalValue();
        String speciesId = uiInputSpecies.getClientId();

        // get confirm password
        UIInput uiInputQuery = ( UIInput ) components.findComponent( "userinp" );
        String currentQuery = uiInputQuery.getLocalValue() == null ? "" : uiInputQuery.getLocalValue().toString();
        String queryId = uiInputQuery.getClientId();

        // Let required="true" do its job.
        if ( selectspecies == null ) {
            FacesMessage msg = new FacesMessage( "Please select a species." );
            msg.setSeverity( FacesMessage.SEVERITY_ERROR );
            fc.addMessage( speciesId, msg );
            fc.renderResponse();
            return;
        } else if ( currentQuery.isEmpty() ) {
            FacesMessage msg = new FacesMessage( "please select a gene symbol." );
            msg.setSeverity( FacesMessage.SEVERITY_ERROR );
            fc.addMessage( queryId, msg );
            fc.renderResponse();
            return;
        }

        Map<String, Collection<Accession>> c = cache.getSymbolToCurrentAccessions().get( selectspecies );
        if ( currentQuery == null || selectspecies == null || c == null || c.get( currentQuery ) == null ) {

            FacesMessage msg = new FacesMessage( "The selected gene symbol could not be found." );
            msg.setSeverity( FacesMessage.SEVERITY_ERROR );
            fc.addMessage( queryId, msg );
            fc.renderResponse();

        }

    }

    public Integer getCurrentSpecies() {
        return currentSpecies;
    }

    public void setCurrentSpecies( Integer currentSpecies ) {
        this.currentSpecies = currentSpecies;
    }

    public String go() {
        // return "track?faces-redirect=true&includeViewParams=true";
        return "track?faces-redirect=true&query=" + query + "&currentSpecies=" + currentSpecies;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery( String query ) {
        this.query = query;
    }

    public List<String> complete( String query ) {
        List<String> result = this.cache.complete( query, currentSpecies );
        System.out.println( "Found " + result.size() + " matches." );
        return result;
    }

    public void setCache( Cache cache ) {
        this.cache = cache;
    }

}