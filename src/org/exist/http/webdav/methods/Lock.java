/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

package org.exist.http.webdav.methods;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.LockToken;
import org.exist.http.webdav.WebDAV;
import org.exist.http.webdav.WebDAVUtil;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Implements the WebDAV LOCK method.
 *
 * @author Dannes Wessels
 * @author wolf
 */
public class Lock extends AbstractWebDAVMethod {
    
    private DocumentBuilderFactory docFactory;
    
    public Lock(BrokerPool pool) {
        super(pool);
        docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);
    }
    
    /* (non-Javadoc)
     * @see org.exist.http.webdav.WebDAVMethod#process(org.exist.security.User, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
     */
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, String path)
            throws ServletException, IOException {
        
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl resource = null;
        try {
            // Get resource
            broker = pool.get(user);
            
            try {
                resource = broker.getXMLResource(path,  org.exist.storage.lock.Lock.READ_LOCK);
            } catch (PermissionDeniedException ex) {
                LOG.error(ex);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            
            if(resource == null) {
                // No document found, maybe a collection
                collection = broker.openCollection(path, org.exist.storage.lock.Lock.READ_LOCK);
                if(collection == null){
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            NOT_FOUND_ERR + " " + path);
                    
                } else {
                    LOG.debug("Locking on collections not supported yet.");
                    response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                            "Locking on collections not supported yet.");
                }
                return;
            }
            
            
            // TODO get information from webDAV client XML
            //LockToken lockToken = getLockParameters(request, response);
            
            // Fill in default information
            LockToken lockToken = new LockToken();
            lockToken.setOwner(user.getName());
            lockToken.setType(LockToken.LOCK_TYPE_WRITE);
            lockToken.setTimeOut(LockToken.LOCK_TIMEOUT_INFINITE);
            lockToken.setScope(LockToken.LOCK_SCOPE_EXCLUSIVE);
            lockToken.setDepth(LockToken.LOCK_DEPTH_0);
            
            if(lockToken==null){
                // Error has been handled,skip test
                LOG.debug("No Locktoken. Stopped Lock request");
                pool.release(broker);
                return;
            }
            
            LOG.debug("Received lock request [" + lockToken.getScope() + "] "
                    +"for owner " + lockToken.getOwner());
            
            // Get current userlock
            User lock = resource.getUserLock();
            
            // Check if Resource is already locked.
            if( lock!=null && !lock.getName().equals(user.getName()) ){
                LOG.debug("Resource is locked.");
                response.sendError(SC_RESOURCE_IS_LOCKED,
                        "Resource is locked by user "+ user.getName() +".");
                return;
            }
            
            // Check for request fo shared lock.
            if(lockToken.getScope() == LockToken.LOCK_SCOPE_SHARED) {
                LOG.debug("Shared locks are not implemented.");
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                        "Shared locks are not implemented.");
                return;
            }
            
            // Fill UUID
            lockToken.createOpaqueLockToken();
            resource.getMetadata().setLockToken(lockToken);
            resource.setUserLock(user);
            
            // Make token persistant
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            broker.storeXMLResource(transaction, resource);
            transact.commit(transaction);
            
            LOG.debug("Sucessfully locked '"+path+"'.");
            
            // Write XML response to client
            lockResource(request, response, resource, lockToken);
            
        } catch (EXistException e) {
            LOG.error(e);
            throw new ServletException(e);
            
        } finally {
            
            if(resource!=null){
                resource.getUpdateLock().release();
            }
            
            if(collection != null){
                collection.release();
            }
            
            if(pool != null){
                pool.release(broker);
            }
            
        }
    }
    
    /**
     *  Get LOCK information from HttpRequest
     *
     * @param request    Http Object
     * @param response   Http Object
     * @throws ServletException
     * @throws IOException
     * @return NULL if error is send to response object, or locktoken with
     *         details about scope, depth and owner
     */
    private LockToken getLockParameters(HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException{
        
        LockToken token = new LockToken();
        
        // Parse XML document
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder;
        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e1) {
            LOG.error(e1);
            throw new ServletException(WebDAVUtil.XML_CONFIGURATION_ERR, e1);
        }
        
        // lockinfo
        Document doc = WebDAVUtil.parseRequestContent(request, response, docBuilder);
        Element lockinfo = doc.getDocumentElement();
        if(!(lockinfo.getLocalName().equals("lockinfo") &&
                lockinfo.getNamespaceURI().equals(WebDAV.DAV_NS))) {
            LOG.debug(WebDAVUtil.UNEXPECTED_ELEMENT_ERR + lockinfo.getNodeName());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    WebDAVUtil.UNEXPECTED_ELEMENT_ERR + lockinfo.getNodeName());
            return null;
        }
        
        Node node = lockinfo.getFirstChild();
        while(node != null) {
            
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                
                if(node.getNamespaceURI().equals(WebDAV.DAV_NS)) {
                    
                    // lockinfo.lockscope
                    if("lockscope".equals(node.getLocalName())) {
                        Node scopeNode = WebDAVUtil.firstElementNode(node);
                        
                        if("exclusive".equals(scopeNode.getLocalName()))
                            token.setScope(LockToken.LOCK_SCOPE_EXCLUSIVE);
                        else if("shared".equals(scopeNode.getLocalName()))
                            token.setScope(LockToken.LOCK_SCOPE_SHARED);;
                    }
                    
                    // lockinfo.locktype
                    if("locktype".equals(node.getLocalName())) {
                        
                        Node typeNode = WebDAVUtil.firstElementNode(node);
                        
                        if(!"write".equals(typeNode.getLocalName())) {
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                                    WebDAVUtil.UNEXPECTED_ELEMENT_ERR + typeNode.getNodeName());
                            return null;
                        }
                        token.setType(LockToken.LOCK_TYPE_WRITE);
                    }
                    
                    // lockinfo.owner
                    if("owner".equals(node.getLocalName())) {
                        Node href = WebDAVUtil.firstElementNode(node);
                        String owner = WebDAVUtil.getElementContent(href);
                        token.setOwner(owner);
                    }
                }
            }
            node = node.getNextSibling();
        }
        
        return token;
    }
    
    
    // Return Lock Info
    private void lockResource(HttpServletRequest request, HttpServletResponse response,
            DocumentImpl resource, LockToken lockToken) throws ServletException, IOException {
        
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/xml");
        response.setCharacterEncoding("utf-8");
        
        ServletOutputStream sos = response.getOutputStream();
        sos.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        sos.println("<D:prop xmlns:D=\"DAV:\">");
        sos.println("<D:lockdiscovery>");
        
        sos.println("<D:activelock>");
        
        // Lock Type
        sos.println("<D:locktype>");
        switch( lockToken.getType() ){
            case LockToken.LOCK_TYPE_WRITE :
                sos.println("<D:write/>");
                break;
            default:
                // This should never be reached
                sos.println("<D:write/>");
                break;
        }
        sos.println("</D:locktype>");
        
        // Lockscope
        sos.println("<D:lockscope>");
        switch( lockToken.getScope() ){
            case LockToken.LOCK_SCOPE_EXCLUSIVE :
                sos.println("<D:exclusive/>");
                break;
            case LockToken.LOCK_SCOPE_SHARED :
                sos.println("<D:shared/>");
                break;
            default:
                // This should never be reached
                sos.println("<D:exclusive/>");
                break;
        }
        sos.println("</D:lockscope>");
        
        // Depth
        switch( lockToken.getDepth() ){
            case LockToken.LOCK_DEPTH_INFINIY :
                sos.println("<D:depth>Infinity</D:depth>");
                break;
            case LockToken.LOCK_DEPTH_0 :
                sos.println("<D:depth>0</D:depth>");
                break;
            case LockToken.LOCK_DEPTH_1 :
                sos.println("<D:depth>1</D:depth>");
                break;
            case LockToken.LOCK_DEPTH_NOT_SET :
                // This should never be reached
                sos.println("<D:depth>not set</D:depth>");
                break;
            default:
                // This should never be reached
                sos.println("<D:depth>null</D:depth>");
                break;
        }
        
        // Owner
        sos.println("<D:owner>");
        sos.println("<D:href>"+lockToken.getOwner()+"</D:href>");
        sos.println("</D:owner>");
        
        // Timeout
        if( lockToken.getTimeOut() == LockToken.LOCK_TIMEOUT_INFINITE ){
            sos.println("<D:timeout>Infinite</D:timeout>");
        } else {
            sos.println("<D:timeout>Second-"+lockToken.getTimeOut()+"</D:timeout>");
        }
        
        // Lock token
        sos.println("<D:locktoken>");
        sos.println("<D:href>opaquelocktoken:"+lockToken.getOpaqueLockToken()+"</D:href>");
        sos.println("</D:locktoken>");
        
        sos.println("</D:activelock>");
        sos.println("</D:lockdiscovery>");
        sos.println("</D:prop>");
        sos.flush();
    }
}
