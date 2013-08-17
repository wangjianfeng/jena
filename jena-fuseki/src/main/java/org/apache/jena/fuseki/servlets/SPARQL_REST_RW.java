/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.fuseki.servlets;

import static java.lang.String.format ;

import java.io.IOException ;
import java.io.InputStream ;

import org.apache.jena.atlas.io.IO ;
import org.apache.jena.atlas.web.ContentType ;
import org.apache.jena.fuseki.FusekiLib ;
import org.apache.jena.fuseki.HttpNames ;
import org.apache.jena.riot.Lang ;
import org.apache.jena.riot.WebContent ;
import org.apache.jena.riot.system.StreamRDF ;
import org.apache.jena.riot.system.StreamRDFLib ;
import org.apache.jena.web.HttpSC ;

import com.hp.hpl.jena.graph.Graph ;
import com.hp.hpl.jena.sparql.graph.GraphFactory ;

/** The WRITE operations added to the READ operations */
public class SPARQL_REST_RW extends SPARQL_REST_R
{
    public SPARQL_REST_RW()
    { super() ; }

    @Override
    protected void doOptions(HttpAction action)
    {
        action.response.setHeader(HttpNames.hAllow, "GET,HEAD,OPTIONS,PUT,DELETE,POST");
        action.response.setHeader(HttpNames.hContentLengh, "0") ;
        success(action) ;
    }
    
    @Override
    protected void doDelete(HttpAction action)
    {
        action.beginWrite() ;
        try {
            Target target = determineTarget(action) ;
            if ( log.isDebugEnabled() )
                log.debug("DELETE->"+target) ;
            boolean existedBefore = target.exists() ; 
            if ( ! existedBefore)
            {
                // commit, not abort, because locking "transactions" don't support abort. 
                action.commit() ;
                errorNotFound("No such graph: "+target.name) ;
            } 
            deleteGraph(action) ;
            action.commit() ;
        }
        finally { action.endWrite() ; }
        SPARQL_ServletBase.successNoContent(action) ;
    }

    @Override
    protected void doPut(HttpAction action)     { doPutPost(action, true) ; }

    @Override
    protected void doPost(HttpAction action)     { doPutPost(action, false) ; }

    private void doPutPost(HttpAction action, boolean overwrite) {
        ContentType ct = FusekiLib.getContentType(action) ;
        if ( ct == null )
            errorBadRequest("No Content-Type:") ;

        // Helper case - if it's a possible HTTP file upload, pretend that's the action.
        if ( WebContent.contentTypeMultiFormData.equalsIgnoreCase(ct.getContentType()) ) {
            String base = wholeRequestURL(action.request) ;
            SPARQL_Upload.upload(action, base) ;
            return ; 
        }

        if ( WebContent.contentTypeMultiMixed.equals(ct.getContentType()) ) {
            error(HttpSC.UNSUPPORTED_MEDIA_TYPE_415, "multipart/mixed not supported") ;
        }
        
        boolean existedBefore = false ;
        if ( action.isTransactional() )
            existedBefore = addDataIntoTxn(action, overwrite) ;
        else
            existedBefore = addDataIntoNonTxn(action, overwrite) ;
            
        if ( existedBefore )
            SPARQL_ServletBase.successNoContent(action) ;
        else
            SPARQL_ServletBase.successCreated(action) ;
    }

    /** Directly add data in a transaction.
     * Assumes recovery from parse errors.
     * Return whether the target existed before.
     * @param action
     * @param cleanDest Whether to remove daat first (true = PUT, false = POST)
     * @return whether the target existed beforehand
     */
    protected static boolean addDataIntoTxn(HttpAction action, boolean overwrite)
    {   

        action.beginWrite();
        Target target = determineTarget(action) ;
        boolean existedBefore = false ;
        try {
            if ( log.isDebugEnabled() )
                log.debug("  ->"+target) ;
            existedBefore = target.exists() ;
            
            Graph g = target.graph() ;
            if ( overwrite && existedBefore )
                clearGraph(target) ;
            StreamRDF sink = StreamRDFLib.graph(g) ;
            incomingData(action, sink);
            action.commit() ;
            return existedBefore ;
        } catch (Exception ex) {
            // If anything went wrong, backout.
            action.abort() ;
            errorOccurred(ex.getMessage()) ;
            return existedBefore ;
        } finally {
            action.endWrite() ;
        }
    }

    /** Add data whether the destination does not support full transactions,
     *  in particular, with no abort, and actions probably going to the real storage
     *  parse errors can lead to partial updates.  Instead, parse to a temporary
     *  graph, then insert that data.  
     * @param action
     * @param cleanDest Whether to remove daat first (true = PUT, false = POST)
     * @return whether the target existed beforehand.
     */
    
    protected static boolean addDataIntoNonTxn(HttpAction action, boolean overwrite) {
        Graph graphTmp = GraphFactory.createGraphMem() ;
        StreamRDF dest = StreamRDFLib.graph(graphTmp) ;
        incomingData(action, dest);
        // Now insert into dataset
        action.beginWrite() ;
        Target target = determineTarget(action) ;
        boolean existedBefore = false ;
        try {
            if ( log.isDebugEnabled() )
                log.debug("  ->"+target) ;
            existedBefore = target.exists() ; 
            if ( overwrite && existedBefore )
                clearGraph(target) ;
            FusekiLib.addDataInto(graphTmp, target.dsg, target.graphName) ;
            action.commit() ;
            return existedBefore ;
        } finally { action.endWrite() ; }
    }
    
    private static void incomingData(HttpAction action, StreamRDF dest) {
        String base = wholeRequestURL(action.request) ;
        ContentType ct = FusekiLib.getContentType(action) ;
        Lang lang = WebContent.contentTypeToLang(ct.getContentType()) ;
        if ( lang == null ) {
            errorBadRequest("Unknown content type for triples: " + ct) ;
            return ;
        }
        InputStream input = null ;
        try { input = action.request.getInputStream() ; } 
        catch (IOException ex) { IO.exception(ex) ; }
    
        int len = action.request.getContentLength() ;
        if ( action.verbose ) {
            if ( len >= 0 )
                log.info(format("[%d]   Body: Content-Length=%d, Content-Type=%s, Charset=%s => %s", action.id, len,
                                ct.getContentType(), ct.getCharset(), lang.getName())) ;
            else
                log.info(format("[%d]   Body: Content-Type=%s, Charset=%s => %s", action.id, ct.getContentType(),
                                ct.getCharset(), lang.getName())) ;
        }
    
        parse(action, dest, input, lang, base) ;
    }

    protected static void deleteGraph(HttpAction action) {
        Target target = determineTarget(action) ;
        if ( target.isDefault )
            target.graph().clear() ;
        else
            action.getActiveDSG().removeGraph(target.graphName) ;
    }

    protected static void clearGraph(Target target) {
        Graph g = target.graph() ;
        g.clear() ;
    }
}
