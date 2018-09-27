/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package com.snoopy.subversion.repository;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * <pre>
 * This example program export contents of the repository directory into file system using
 * SVNKit library low level API.
 *
 * In general, approach we are using in this example is the same that is used for operations
 * like 'update', 'remote status', 'diff' or 'checkout'. The export operation is the most
 * simple one and allows to demonstrate this approach without going too much into the details.
 *
 * You may find and an article describing this (update) technique at
 * http://svnkit.com/kb/dev-guide-update-operation.html
 *
 * To perform any update-like operation one have to do the following:
 *
 * 1. Report the state of the client's working copy to the Subversion server. Of course, it could be
 *    'virtual' working copy, not necessary stored in the Subversion wc format or, in case of export or
 *    diff operation there could be no working copy at all, which is reflected in report.
 *
 *    Report is performed with the help ISVNReporter instance that is passed to the client's ISVNReporterBaton
 *    object at the moment report have to be sent.
 *
 * 2. Process instructions received from the server. These instructions describes how to modify working copy
 *    to make it be at the desirable revision. Amount of instructions depends on the report sent by the client.
 *    Different operations process received instructions in different manner. For instance, update operation
 *    updates working copy in the filsystem, remote status operation merely logs files and directories that
 *    have to be updated and displays this information.
 *
 *    With SVNKit API you may implement your own processing code, e.g. repository replication or custom merging code.
 *    ISVNEditor is the interface which implementations process update instructions sent by the server and in
 *    this example ISVNEditor implementation (ExportEditor) creates files and directories corresponding to those
 *    in the repository.
 *
 * </pre>
 * @ClassName:  Export.java
 * @author: LiHaiQing
 * @date:   2018/9/27 10:53
 * @version V1.0.0
 */
public class Export {
    
    public static void main(String[] args) {
        /**
         * Initialize the library. It must be done before calling any 
         * method of the library.
         */
        setupLibrary();
        
        /**
         * Run export example and process error if any.
         */
        try {
            exportExample();
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            /**
             * Display all tree of error messages. 
             * Utility method SVNErrorMessage.getFullMessage() may be used instead of the loop.
             */
            while(err != null) {
                System.err.println(err.getErrorCode().getCode() + " : " + err.getMessage());
                err = err.getChildErrorMessage();
            }
            System.exit(1);
        }
        System.exit(0);
    }

    private static void exportExample() throws SVNException {

        SVNURL url = SVNURL.parseURIEncoded("http://svn.svnkit.com/repos/svnkit/trunk/doc");
        String userName = "foo";
        String userPassword = "bar";
        
        /**
         * Prepare filesystem directory (export destination).
         */
        File exportDir = new File("export");
        if (exportDir.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Path ''{0}'' already exists", exportDir);
            throw new SVNException(err);
        }
        exportDir.mkdirs();

        /**
         * Create an instance of SVNRepository class. This class is the main entry point 
         * for all "low-level" Subversion operations supported by Subversion protocol. 
         * 
         * These operations includes browsing, update and commit operations. See 
         * SVNRepository methods javadoc for more details.
         */
        SVNRepository repository = SVNRepositoryFactory.create(url);

        /**
         * User's authentication information (name/password) is provided via  an
         * ISVNAuthenticationManager  instance.  SVNWCUtil  creates  a   default 
         * authentication manager given user's name and password.
         * 
         * Default authentication manager first attempts to use provided user name 
         * and password and then falls back to the credentials stored in the 
         * default Subversion credentials storage that is located in Subversion 
         * configuration area. If you'd like to use provided user name and password 
         * only you may use BasicAuthenticationManager class instead of default 
         * authentication manager:
         * 
         *  authManager = new BasicAuthenticationsManager(userName, userPassword);
         *  
         * You may also skip this point - anonymous access will be used. 
         */
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(userName, userPassword);
        repository.setAuthenticationManager(authManager);

        /**
         * Get type of the node located at URL we used to create SVNRepository.
         * 
         * "" (empty string) is path relative to that URL, 
         * -1 is value that may be used to specify HEAD (latest) revision.
         */
        SVNNodeKind nodeKind = repository.checkPath("", -1);
        if (nodeKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "No entry at URL ''{0}''", url);
            throw new SVNException(err);
        } else if (nodeKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry at URL ''{0}'' is a file while directory was expected", url);
            throw new SVNException(err);
        }

        /**
         * Get latest repository revision. We will export repository contents at this very revision.
         */
        long latestRevision = repository.getLatestRevision();
        
        /**
         * Create reporterBaton. This class is responsible for reporting 'wc state' to the server.
         * 
         * In this example it will always report that working copy is empty to receive update
         * instructions that are sufficient to create complete directories hierarchy and get full
         * files contents.
         */
        ISVNReporterBaton reporterBaton = new ExportReporterBaton(latestRevision);
        
        /**
         * Create editor. This class will process update instructions received from the server and 
         * will create directories and files accordingly.
         * 
         * As we've reported 'emtpy working copy', server will only send 'addDir/addFile' instructions
         * and will never ask our editor implementation to modify a file or directory properties. 
         */
        ISVNEditor exportEditor = new ExportEditor(exportDir);
        
        /**
         * Now ask SVNKit to perform generic 'update' operation using our reporter and editor.
         * 
         * We are passing:
         * 
         * - revision from which we would like to export
         * - null as "target" name, to perform export from the URL SVNRepository was created for, 
         *   not from some child directory.
         * - reporterBaton
         * - exportEditor.  
         */
        repository.update(latestRevision, null, true, reporterBaton, exportEditor);
        
        System.out.println("Exported revision: " + latestRevision);
    }

    /**
     * ReporterBaton implementation that always reports 'empty wc' state.
     */
    private static class ExportReporterBaton implements ISVNReporterBaton {

        private long exportRevision;
        
        public ExportReporterBaton(long revision){
            exportRevision = revision;
        }
        @Override
        public void report(ISVNReporter reporter) throws SVNException {
            try {
                /**
                 * Here empty working copy is reported.
                 * 
                 * ISVNReporter includes methods that allows to report mixed-rev working copy
                 * and even let server know that some files or directories are locally missing or
                 * locked. 
                 */
                reporter.setPath("", null, exportRevision, SVNDepth.INFINITY, true);
                
                /*
                 * Don't forget to finish the report!
                 */
                reporter.finishReport(); 
            } catch (SVNException svne) {
                reporter.abortReport();
                System.out.println("Report failed.");
            }
        }
    }

    /**
     * ISVNEditor implementation that will add directories and files into the target directory
     * accordingly to update instructions sent by the server. 
     */
    private static class ExportEditor implements ISVNEditor {
        
        private File myRootDirectory;
        private SVNDeltaProcessor myDeltaProcessor;
        
        /**
         * root - the local directory where the node tree is to be exported into.
         */
        public ExportEditor(File root) {
            myRootDirectory = root;
            /*
             * Utility class that will help us to transform 'deltas' sent by the 
             * server to the new file contents.  
             */
            myDeltaProcessor = new SVNDeltaProcessor();
        }

        /**
         * Server reports revision to which application of the further 
         * instructions will update working copy to.
         */
        @Override
        public void targetRevision(long revision) throws SVNException {
        }

        /**
         * Called before sending other instructions.
         */
        @Override
        public void openRoot(long revision) throws SVNException {
        }
        
        /**
         * Called when a new directory has to be added.
         * 
         * For each 'addDir' call server will call 'closeDir' method after 
         * all children of the added directory are added.
         * 
         * This implementation creates corresponding directory below root directory. 
         */
        @Override
        public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
            File newDir = new File(myRootDirectory, path);
            if (!newDir.exists()) {
                if (!newDir.mkdirs()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "error: failed to add the directory ''{0}''.", newDir);
                    throw new SVNException(err);
                }
            }
            System.out.println("dir added: " + path);
        }
        
        /**
         * Called when there is an existing directory that has to be 'opened' either 
         * to modify this directory properties or to process other files and directories 
         * inside this directory. 
         * 
         * In case of export this method will never be called because we reported 
         * that our 'working copy' is empty and so server knows that there are
         * no 'existing' directories. 
         */
        @Override
        public void openDir(String path, long revision) throws SVNException {
        }

        /**
         * Instructs to change opened or added directory property.
         *   
         * This method is called to update properties set by the user as well
         * as those created automatically, like "svn:committed-rev". 
         * See SVNProperty class for default property names. 
         * 
         * When property has to be deleted value will be 'null'. 
         */
        @Override
        public void changeDirProperty(String name, SVNPropertyValue property) throws SVNException {
		}

        /**
         * Called when a new file has to be created.
         * 
         * For each 'addFile' call server will call 'closeFile' method after 
         * sending file properties and contents. 
         * 
         * This implementation creates empty file below root directory, file contents
         * will be updated later, and for empty files may not be sent at all. 
         */
        @Override
        public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
            File file = new File(myRootDirectory, path);
            if (file.exists()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "error: exported file ''{0}'' already exists!", file);
                throw new SVNException(err);
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "error: cannot create new  file ''{0}''", file);
                throw new SVNException(err);
            }
        }
        
        /**
         * Called when there is an existing files that has to be 'opened' either 
         * to modify file contents or properties.
         * 
         * In case of export this method will never be called because we reported 
         * that our 'working copy' is empty and so server knows that there are
         * no 'existing' files. 
         */
        @Override
        public void openFile(String path, long revision) throws SVNException {
        }

        /**
         * Instructs to add, modify or delete file property.
         * In this example we skip this instruction, but 'real' export operation
         * may inspect 'svn:eol-style' or 'svn:mime-type' property values to 
         * transfor file contents propertly after receiving.
         */
        @Override
        public void changeFileProperty(String path, String name, SVNPropertyValue property) throws SVNException {
		}

        /**
         * Called before sending 'delta' for a file. Delta may include instructions
         * on how to create a file or how to modify existing file. In this example
         * delta will always contain instructions on how to create a new file and so
         * we set up deltaProcessor with 'null' base file and target file to which we would 
         * like to store the result of delta application. 
         */
        @Override
        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
            myDeltaProcessor.applyTextDelta((File) null, new File(myRootDirectory, path), false);
        }

        /**
         * Server sends deltas in form of 'diff windows'. Depending on the file size 
         * there may be several diff windows. Utility class SVNDeltaProcessor processes 
         * these windows for us.
         */
        @Override
        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow)   throws SVNException {
            return myDeltaProcessor.textDeltaChunk(diffWindow);
        }
        
        /**
         * Called when all diff windows (delta) is transferred. 
         */
        @Override
        public void textDeltaEnd(String path) throws SVNException {
            myDeltaProcessor.textDeltaEnd();
        }
        
        /**
         * Called when file update is completed. 
         * This call always matches addFile or openFile call.
         */
        @Override
        public void closeFile(String path, String textChecksum) throws SVNException {
            System.out.println("file added: " + path);
        }

        /**
         * Called when all child files and directories are processed. 
         * This call always matches addDir, openDir or openRoot call.
         */
        @Override
        public void closeDir() throws SVNException {
        }

        /**
         * Insturcts to delete an entry in the 'working copy'. Of course will not be 
         * called during export operation. 
         */
        @Override
        public void deleteEntry(String path, long revision) throws SVNException {
        }
        
        /**
         * Called when directory at 'path' should be somehow processed, 
         * but authenticated user (or anonymous user) doesn't have enough 
         * access rights to get information on this directory (properties, children).
         */
        @Override
        public void absentDir(String path) throws SVNException {
        }

        /**
         * Called when file at 'path' should be somehow processed,
         * but authenticated user (or anonymous user) doesn't have enough 
         * access rights to get information on this file (contents, properties).
         */
        @Override
        public void absentFile(String path) throws SVNException {
        }        
        
        /**
         * Called when update is completed. 
         */
        @Override
        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }
        
        /**
         * Called when update is completed with an error or server 
         * requests client to abort update operation. 
         */
        @Override
        public void abortEdit() throws SVNException {
        }

    }

    /**
     * Initializes the library to work with a repository via 
     * different protocols.
     */
    private static void setupLibrary() {
        /**
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
        /**
         * For using over svn:// and svn+xxx://
         */
        SVNRepositoryFactoryImpl.setup();
        
        /**
         * For using over file:///
         */
        FSRepositoryFactory.setup();
    }
}