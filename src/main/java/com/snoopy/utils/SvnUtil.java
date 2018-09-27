/**
 * <pre>
 * Copyright (C), 2018, 杭州共道科技
 * FileName: SvnUtil
 * Author:   LiHaiQing
 * Date:     2018/8/27 11:34
 * Description: Svn工具类
 * History:
 * <author>          <time>          <version>          <desc>
 * 作者姓名           修改时间           版本号              描述
 * </pre>
 */
package com.snoopy.utils;

import java.io.File;
import java.io.IOException;

import com.snoopy.subversion.handler.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;

/**
 * @ClassName: SvnUtil.java
 * @Description: svn工具类
 * @author: LiHaiQing
 * @date: 2018/8/27 12:18
 * @version V1.0
 */
public class SvnUtil {

    private static Logger logger = LoggerFactory.getLogger(SvnUtil.class);

    private SvnUtil(){
    }

    public static SvnUtil getInstance() {
        return Single.INSTANCE.getInstance();
    }

    enum Single {
                 // 单例
                 INSTANCE;

        private SvnUtil single = new SvnUtil();

        public SvnUtil getInstance() {
            return single;
        }
    }

    private static SVNClientManager ourClientManager;
    private static ISVNEventHandler myCommitEventHandler;
    private static ISVNEventHandler myUpdateEventHandler;
    private static ISVNEventHandler myWCEventHandler;

    /**
     * 获取svn历史版本<br>
     * 
     * @Title: getSvnHistory
     * @author LiHaiQing
     * @param: [url, name, password, startRevision, endRevision, limit]
     */
    public void getSvnHistory(String url, String name, String password, Long startRevision, Long endRevision,
                              Long limit) {
        if (startRevision == null || startRevision < 0) {
            startRevision = 0L;
        }
        // HEAD (the latest) revision
        if (startRevision == null || startRevision < 0) {
            endRevision = -1L;
        }

        setupLibrary();

        SVNRepository repository = null;
        try {
            repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
        } catch (SVNException svnException) {
            logger.error("error while creating an SVNRepository for the location {} :{}", url,
                         svnException.getMessage());
        }
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(name,
                                                                                             password.toCharArray());
        repository.setAuthenticationManager(authManager);

        try {
            // Gets the latest revision number of the repository
            endRevision = repository.getLatestRevision();
        } catch (SVNException svnException) {
            logger.error("error while fetching the latest repository revision: {}", svnException.getMessage());
        }
        try {
            repository.log(new String[] { "" }, startRevision, endRevision, true, true, limit,
                           new ISVNLogEntryHandler() {

                               @Override
                               public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                               }
                           });
        } catch (SVNException svnException) {
            logger.error("error while collecting log information for  {} :{}", url, svnException.getMessage());
        }
    }

    /**
     * svn回滚代码 <br>
     *
     * @Title: rollback
     * @author LiHaiQing
     * @param: [trunkUrl, branchName, username, password, rollbackVersion]
     */
    public void rollback(String trunkUrl, String username, String password, String rollbackVersion,
                         String rollbackMessage) throws SVNException, IOException {
        setupLibrary();
        SVNURL repositoryURL = null;
        try {
            repositoryURL = SVNURL.parseURIEncoded(trunkUrl);
        } catch (SVNException e) {
            logger.info("SVNURL.parseURIEncoded failure ", e);
            throw new IOException("SVNURL.parseURIEncoded failure...");
        }
        String workingCopyName = trunkUrl.substring(trunkUrl.lastIndexOf("/") + 1);
        if ("".equals(workingCopyName)) {
            logger.info("trunkUrl cannot end with a '/' : {}", trunkUrl);
            throw new IOException("trunkUrl cannot end with a '/' : " + trunkUrl);
        }
        File workingCopyPath = File.createTempFile(workingCopyName, "");
        if (!workingCopyPath.delete()) {
            logger.info("Could not delete temporary file :{}", workingCopyPath);
            throw new IOException("Could not delete temporary file " + workingCopyPath);
        }
        myCommitEventHandler = new CommitEventHandler();
        myUpdateEventHandler = new UpdateEventHandler();
        myWCEventHandler = new WCEventHandler();

        ISVNOptions options = SVNWCUtil.createDefaultOptions(true);
        ourClientManager = SVNClientManager.newInstance((DefaultSVNOptions) options, username, password);

        ourClientManager.getCommitClient().setEventHandler(myCommitEventHandler);
        ourClientManager.getUpdateClient().setEventHandler(myUpdateEventHandler);
        ourClientManager.getWCClient().setEventHandler(myWCEventHandler);

        // 0、检出项目到本地
        logger.info("Checking out a working copy from '{}'... ", repositoryURL);
        try {
            checkout(repositoryURL, SVNRevision.HEAD, workingCopyPath, true);
        } catch (SVNException svne) {
            logger.error("error while checking out a working copy for the location '{}'... ", repositoryURL, svne);
        }
        try {
            showInfo(workingCopyPath, SVNRevision.WORKING, true);
        } catch (SVNException svne) {
            logger.error("error while recursively getting info for the working copy at'{}'... ",
                         workingCopyPath.getAbsolutePath(), svne);
        }
        // 获取SVNDiffClient
        SVNDiffClient diffClient = ourClientManager.getDiffClient();
        diffClient.setIgnoreExternals(false);
        DefaultSVNOptions conflictOptions = (DefaultSVNOptions) diffClient.getOptions();
        // 配置一个 ConflictResolverHandler
        conflictOptions.setConflictHandler(new ConflictResolverHandler());
        long committedRevision = -1;

        // 1、更新本地库为最新代码(TODO 此处可以不需要update，因为每次回滚操作都是检出最新的，不清楚会不会发生在回滚的时候有人提交代码，update成本很低速度快，倒是不会浪费时间，暂时保留)
        logger.info("Updating '{}'... ", workingCopyPath.getAbsolutePath());
        try {
            update(workingCopyPath, SVNRevision.HEAD, true);
        } catch (SVNException svne) {
            logger.error("error while recursively updating the working copy at '{}' ",
                         workingCopyPath.getAbsolutePath(), svne);
        }
        // 2、merge想要回退的版本到本地库，冲突解决方案为采用合并过来的版本，以此来达到回滚目的
        logger.info("rollback start... version to [{}]  ", rollbackVersion);
        SVNURL url1 = SVNURL.parseURIEncoded(trunkUrl);
        SVNURL url2 = SVNURL.parseURIEncoded(trunkUrl);
        diffClient.doMerge(url1, SVNRevision.HEAD, url2, SVNRevision.parse(rollbackVersion), workingCopyPath,
                           SVNDepth.INFINITY, false, true, false, false);
        // 3、将合并的本地版本提交，最新版本即回退的版本
        logger.info("Committing changes for '{}'... ", workingCopyPath.getAbsolutePath());
        try {
            committedRevision = commit(workingCopyPath, false, rollbackMessage).getNewRevision();
        } catch (SVNException e) {
            logger.error("error while committing changes to the working copy at '{}' ",
                         workingCopyPath.getAbsolutePath(), e);
        }
        logger.info("rollback end. Committed to revision '{}'... ", committedRevision);
        if (committedRevision == -1) {
            // 经过测试得知，如果回滚的版本与当前版本没有任何区别，提交是无效的
            logger.info("rollback version not change, so commit invalid...");
        }
        FileUtils.deleteDirectory(workingCopyPath);
    }

    /**
     * Initializes the library to work with a repository via different protocols.
     */
    private static void setupLibrary() {
        // For using over http:// and https://
        DAVRepositoryFactory.setup();
        // For using over svn:// and svn+xxx://
        SVNRepositoryFactoryImpl.setup();
        // For using over file:///
        FSRepositoryFactory.setup();
    }

    /**
     * checkout <br>
     *
     * @Title: checkout
     * @author LiHaiQing
     * @param: [url, revision, destPath, isRecursive]
     */
    private long checkout(SVNURL url, SVNRevision revision, File destPath, boolean isRecursive) throws SVNException {
        SVNUpdateClient updateClient = ourClientManager.getUpdateClient();
        /**
         * sets externals not to be ignored during the checkout
         */
        updateClient.setIgnoreExternals(false);
        /**
         * returns the number of the revision at which the working copy is
         */
        return updateClient.doCheckout(url, destPath, revision, revision, SVNDepth.fromRecurse(isRecursive), false);
    }

    /**
     * InfoHandler displays information for each entry in the console (in the manner of the native Subversion command
     * line client) <br>
     *
     * @Title: showInfo
     * @author LiHaiQing
     * @param: [wcPath, revision, isRecursive]
     */
    private void showInfo(File wcPath, SVNRevision revision, boolean isRecursive) throws SVNException {
        ourClientManager.getWCClient().doInfo(wcPath, SVNRevision.UNDEFINED, revision,
                                              SVNDepth.getInfinityOrEmptyDepth(isRecursive), null, new InfoHandler());
    }

    /**
     * 提交 <br>
     *
     * @Title: commit
     * @author LiHaiQing
     * @param: [wcPath, keepLocks, commitMessage]
     */
    private SVNCommitInfo commit(File wcPath, boolean keepLocks, String commitMessage) throws SVNException {
        return ourClientManager.getCommitClient().doCommit(new File[] { wcPath }, keepLocks, commitMessage, null, null,
                                                           false, true, SVNDepth.fromRecurse(true));
    }

    /**
     * 更新 <br>
     *
     * @Title: update
     * @author LiHaiQing
     * @param: [workingCopyPath, updateToRevision, isRecursive]
     */
    private long update(File workingCopyPath, SVNRevision updateToRevision, boolean isRecursive) throws SVNException {

        SVNUpdateClient updateClient = ourClientManager.getUpdateClient();
        updateClient.setIgnoreExternals(false);
        return updateClient.doUpdate(workingCopyPath, updateToRevision, SVNDepth.fromRecurse(isRecursive), false,
                                     false);
    }

}
