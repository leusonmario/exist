/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.dom;

import java.io.EOFException;
import java.io.IOException;

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.MimeType;
import org.w3c.dom.DocumentType;

public class DocumentMetadata {
    
    public final static byte HAS_DOCTYPE = 1;
    
    public final static byte HAS_LOCKTOKEN = 2;
    
    public final static int REINDEX_ALL = -1;
    
    /** the mimeType of the document */
    private String mimeType = MimeType.XML_TYPE.getName();
    
    /** the creation time of this document */
    private long created = 0;
    
    /** time of the last modification */
    private long lastModified = 0;
    
    /** the number of data pages occupied by this document */
    private int pageCount = 0;
    
    /** contains the user id if a user lock is held on this resource */
    private int userLock = 0;
    
    /** the document's doctype declaration - if specified. */
    private DocumentType docType = null;
    
    /** TODO associated locktoken - if available */
    private LockToken lockToken = null;
    
    private transient NodeIndexListener listener = NullNodeIndexListener.INSTANCE;
    
    protected transient int splitCount = 0;
    
    /**
     * if set to > -1, the document needs to be partially reindexed
     *  - beginning at the tree-level defined by reindex
     */
    protected transient int reindex = REINDEX_ALL;
    
    public DocumentMetadata() {
    }
    
    public DocumentMetadata(DocumentMetadata other) {
        this.mimeType = other.mimeType;
        this.created = other.created;
        this.lastModified = other.lastModified;
    }
    
    public long getCreated() {
        return created;
    }
    
    public void setCreated(long created) {
        this.created = created;
        if(lastModified == 0)
            lastModified = created;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    /**
     * Returns the number of pages currently occupied by this document.
     *
     * @return
     */
    public int getPageCount() {
        return pageCount;
    }
    
    /**
     * Set the number of pages currently occupied by this document.
     * @param count
     */
    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }
    
    public void incPageCount() {
        ++pageCount;
    }
    
    public void decPageCount() {
        --pageCount;
    }
    
    public void write(VariableByteOutputStream ostream) throws IOException {
        ostream.writeLong(created);
        ostream.writeLong(lastModified);
        ostream.writeUTF(mimeType);
        ostream.writeInt(pageCount);
        ostream.writeInt(userLock);
        
        if (docType != null) {
            ostream.writeByte(HAS_DOCTYPE);
            ((DocumentTypeImpl) docType).write(ostream);
        } else {
            ostream.writeByte((byte) 0);
        }

        // TODO added by dwes
        if (lockToken != null) {
            ostream.writeByte(HAS_LOCKTOKEN);
            lockToken.write(ostream);
        } else {
            ostream.writeByte((byte) 0);
        }

        
    }
    
    public void read(VariableByteInput istream) throws IOException, EOFException {
        created = istream.readLong();
        lastModified = istream.readLong();
        mimeType = istream.readUTF();
        pageCount = istream.readInt();
        userLock = istream.readInt();
        
        if (istream.readByte() == HAS_DOCTYPE) {
            docType = new DocumentTypeImpl();
            ((DocumentTypeImpl) docType).read(istream);
        } else {
            docType = null;
        }
        
        // TODO added by dwes
        if(istream.readByte() == HAS_LOCKTOKEN){
            lockToken = new LockToken();
            lockToken.read(istream);
        } else {
            lockToken = null;
        }
    }
    
    public int getUserLock() {
        return userLock;
    }
    
    public void setUserLock(int userLock) {
        this.userLock = userLock;
    }
    
    public LockToken getLockToken(){
        return lockToken;
    }
    
    public void setLockToken(LockToken token){
        lockToken=token;
    }
    
    public DocumentType getDocType() {
        return docType;
    }
    
    public void setDocType(DocumentType docType) {
        this.docType = docType;
    }
    
    public NodeIndexListener getIndexListener() {
        return listener;
    }
    
    public void clearIndexListener() {
        listener = NullNodeIndexListener.INSTANCE;
    }
    
    public void setIndexListener(NodeIndexListener listener) {
        this.listener = listener;
    }
    
    public int reindexRequired() {
        return reindex;
    }
    
    public void setReindexRequired(int level) {
        this.reindex = level;
    }
    
    /**
     * Increase the page split count of this document. The number
     * of pages that have been split during inserts serves as an
     * indicator for the
     *
     */
    public void incSplitCount() {
        splitCount++;
    }
    
    public int getSplitCount() {
        return splitCount;
    }
    
    public void setSplitCount(int count) {
        splitCount = count;
    }
}
