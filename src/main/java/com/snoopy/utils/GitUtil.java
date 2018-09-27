/**
 * <pre>
 * Copyright (C), 2018, 杭州共道科技
 * FileName: GitUtil
 * Author:   LiHaiQing
 * Date:     2018/8/28 13:48
 * Description: Git工具类
 * History:
 * <author>          <time>          <version>          <desc>
 * 作者姓名           修改时间           版本号              描述
 * </pre>
 */
package com.snoopy.utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Git 工具类 <br>
 * 
 * @ClassName: GitUtil.java
 * @author: LiHaiQing
 * @date: 2018/9/6 11:24
 * @version V1.0.0
 */
public class GitUtil {

    private static Logger logger = LoggerFactory.getLogger(GitUtil.class);

    private GitUtil(){
    }

    public static GitUtil getInstance() {
        return Single.INSTANCE.getInstance();
    }

    enum Single {
                 // 单例
                 INSTANCE;

        private GitUtil single = new GitUtil();

        public GitUtil getInstance() {
            return single;
        }
    }

    /**
     * 克隆主干到本地 <br>
     * 
     * @Title: cloneRepository
     * @author LiHaiQing
     * @param: [remoteUrl, username, password]
     */
    public File cloneRepository(String remoteUrl, String username, String password) throws IOException,
                                                                                    GitAPIException {
        remoteUrl = "http://xxx/xxx/xxx.git";
        username = "xxx";
        password = "xxx";
        File localPath = File.createTempFile("TestGitRepository", "");
        if (!localPath.delete()) {
            logger.error("Could not delete temporary file :{}", localPath);
            throw new IOException("Could not delete temporary file " + localPath);
        }
        logger.info("Cloning from {} to {}", remoteUrl, localPath);
        UsernamePasswordCredentialsProvider provider = new UsernamePasswordCredentialsProvider(username, password);
        try (Git result = Git.cloneRepository().setCredentialsProvider(provider).setBranch("master").setURI(remoteUrl).setDirectory(localPath).call()) {
            logger.info("Having repository :{}", result.getRepository().getDirectory());
        }
        return localPath;
    }

    /**
     * 查看远程Git历史记录 <br>
     * 
     * @Title: gitRemoteLog
     * @author LiHaiQing
     * @param: [remoteUrl, username, password]
     */
    public void gitRemoteLog(String remoteUrl, String username, String password) throws IOException, GitAPIException {
        File localPath = File.createTempFile("TestGitRepository", "");
        if (!localPath.delete()) {
            logger.error("Could not delete temporary file :{}", localPath);
            throw new IOException("Could not delete temporary file " + localPath);
        }
        logger.info("Cloning from {} to {}", remoteUrl, localPath);
        UsernamePasswordCredentialsProvider provider = new UsernamePasswordCredentialsProvider(username, password);
        try (Git result = Git.cloneRepository().setCredentialsProvider(provider).setBranch("master").setURI(remoteUrl).setDirectory(localPath).call()) {
            logger.info("Having repository :{}", result.getRepository().getDirectory());
            Iterable<RevCommit> logs = result.log().call();
            logs.forEach(revCommit -> {
                System.out.println("------------------------------------------------------");
                // 提交信息
                ObjectId objectId = revCommit.getId();
                System.out.println("commit " + objectId.getName());
                // 作者信息
                PersonIdent authorIdent = revCommit.getAuthorIdent();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                simpleDateFormat.setTimeZone(authorIdent.getTimeZone());
                String authorTime = simpleDateFormat.format(authorIdent.getWhen());
                System.out.println("Author: " + authorIdent.getName() + "< " + authorIdent.getEmailAddress() + "> "
                                   + authorTime);
                // 提交者信息
                PersonIdent committerIdent = revCommit.getCommitterIdent();
                simpleDateFormat.setTimeZone(committerIdent.getTimeZone());
                String commitTime = simpleDateFormat.format(committerIdent.getWhen());
                System.out.println("Committer: " + committerIdent.getName() + " <" + committerIdent.getEmailAddress()
                                   + "> " + commitTime);
                // 父
                RevCommit[] revCommitParents = revCommit.getParents();
                for (int i = 0; i < revCommitParents.length; i++) {
                    RevCommit temp = revCommitParents[i];
                    System.out.println("Parent: " + temp.getId().getName() + " (" + temp.getFullMessage() + ")");
                }
                String fullMessage = revCommit.getFullMessage();
                System.out.println("fullMessage:" + fullMessage);
            });
        }
        FileUtils.deleteDirectory(localPath);
    }

    /**
     * Git Clone到本地查看 日志 <br>
     * 
     * @Title: gitLocalLog
     * @author LiHaiQing
     * @param: [localPath]
     */
    public void gitLocalLog(File localPath) throws IOException, GitAPIException {
        String gitRoot = localPath.getAbsolutePath() + "\\.git";
        logger.info("gitLog gitRoot path :{}", gitRoot);
        try (Git git = Git.open(new File(gitRoot))) {
            Iterable<RevCommit> logs = git.log().call();
            logs.forEach(revCommit -> {
                System.out.println("-------------------------------------------------------");
                // 提交信息
                ObjectId objectId = revCommit.getId();
                System.out.println("commit " + objectId.getName());
                // 作者信息
                PersonIdent authorIdent = revCommit.getAuthorIdent();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                simpleDateFormat.setTimeZone(authorIdent.getTimeZone());
                String authorTime = simpleDateFormat.format(authorIdent.getWhen());
                System.out.println("Author: " + authorIdent.getName() + "< " + authorIdent.getEmailAddress() + "> "
                                   + authorTime);
                // 提交者信息
                PersonIdent committerIdent = revCommit.getCommitterIdent();
                simpleDateFormat.setTimeZone(committerIdent.getTimeZone());
                String commitTime = simpleDateFormat.format(committerIdent.getWhen());
                System.out.println("Committer: " + committerIdent.getName() + " <" + committerIdent.getEmailAddress()
                                   + "> " + commitTime);
                // 父
                RevCommit[] revCommitParents = revCommit.getParents();
                for (int i = 0; i < revCommitParents.length; i++) {
                    RevCommit temp = revCommitParents[i];
                    System.out.println("Parent: " + temp.getId().getName() + " (" + temp.getFullMessage() + ")");
                }
                String fullMessage = revCommit.getFullMessage();
                System.out.println("fullMessage:" + fullMessage);
            });
        }
    }

    /**
     * 查看refLog <br>
     * 
     * @Title: gitLocalRefLog
     * @author LiHaiQing
     * @param: [localPath]
     */
    public void gitLocalRefLog(File localPath) throws IOException, GitAPIException {
        String gitRoot = localPath.getAbsolutePath() + "\\.git";
        logger.info("gitLog gitRoot path :{}", gitRoot);
        try (Git git = Git.open(new File(gitRoot))) {
            Collection<ReflogEntry> reflogEntries = git.reflog().setRef("refs/heads/master").call();
            System.out.println("----------------------" + reflogEntries.size() + "-----------------------------------");
            reflogEntries.stream().forEach(reflogEntry -> {
                System.out.println("Reflog: " + reflogEntry.toString());
            });
        }
    }

    /**
     * 如果想恢复到之前某个提交的版本，且那个版本之后提交的版本我们都不要了，就可以用这种方法。(划重点：不会有冲突)</br>
     * 这个慎用，建议在使用的时候拉一个分支（注意命名规范）进行备份。
     * 
     * @Title: rollbackByReset
     * @author LiHaiQing
     * @param: [localPath, revision, username, password]
     */
    public void rollbackByReset(String remoteUrl, String revision, String username, String password) throws IOException,
                                                                                                     GitAPIException {

        File localPath = File.createTempFile("GitApplication", "");
        if (!localPath.delete()) {
            logger.error("Could not delete temporary file :{}", localPath);
            throw new IOException("Could not delete temporary file " + localPath);
        }
        logger.info("Cloning from {} to {}", remoteUrl, localPath);
        UsernamePasswordCredentialsProvider provider = new UsernamePasswordCredentialsProvider(username, password);
        try (Git git = Git.cloneRepository().setCredentialsProvider(provider).setBranch("master").setURI(remoteUrl).setDirectory(localPath).call()) {
            logger.info("Having repository :{}", git.getRepository().getDirectory());
            logger.info("rollbackByReset gitRoot path :{}", localPath);
            Repository repository = git.getRepository();
            ObjectId objId = repository.resolve(revision);
            logger.info("rFound objId:  :{}", objId);
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(objId);
                logger.info("Found Commit: {}", commit);
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(revision).call();
                git.push().setCredentialsProvider(provider).setForce(true).call();
                walk.dispose();
            }
        }
        FileUtils.deleteDirectory(localPath);
    }

    /**
     * 如果我们想恢复之前的某一版本（该版本不是merge类型,如果是merge就要选择了）；</br>
     * 但是又想保留该目标版本后面的版本，记录下这整个版本变动流程，就可以用这种方法。(划重点：会产生冲突)</br>
     * 经过试验发现：revert是代码回退到原来版本，但不会删除提交历史</br>
     * git revert commit_id :commit_id代表的版本被撤销，所以最终是回滚到commit id的前一次提交。
     * 
     * @Title: rollbackByRevert
     * @author LiHaiQing
     * @param: [localPath, revision, username, password]
     */
    public void rollbackByRevert(File localPath, String revision, String username, String password) throws IOException,
                                                                                                    GitAPIException {

        String gitRoot = localPath.getAbsolutePath() + "\\.git";
        logger.info("rollbackByRevert gitRoot path :{}", gitRoot);
        username = "xxx";
        password = "xxx";
        try (Git git = Git.open(new File(gitRoot))) {
            Repository repository = git.getRepository();
            ObjectId objId = repository.resolve(revision);
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit revCommit = walk.parseCommit(objId);
                logger.info("Found Commit: {}", revCommit);
                RevCommit[] parents = revCommit.getParents();
                if (parents.length != 0) {
                    String preVision = revCommit.getParent(0).getName();
                    logger.info("Found preVision: {}", preVision);
                    git.revert().include(objId).call();
                    UsernamePasswordCredentialsProvider provider = new UsernamePasswordCredentialsProvider(username,
                                                                                                           password);
                    git.push().setCredentialsProvider(provider).setForce(true).call();
                    walk.dispose();
                } else {
                    // TODO 抛出异常，not found preVersion,can't revert;
                }
            }
        }
        FileUtils.deleteDirectory(localPath);
    }

    /**
     * 回滚：符合需求 <br>
     * 原理：不会改变HEAD头指针，主要使用于指定版本的文件覆盖工作区中对应的文件。
     * 
     * @Title: rollback
     * @author LiHaiQing
     * @param: [remoteUrl, revision, username, password]
     */
    public void rollback(String remoteUrl, String revision, String username, String password) throws IOException,
                                                                                              GitAPIException {
        File localPath = File.createTempFile("rollbackGitRepository", "");
        if (!localPath.delete()) {
            logger.info("Could not delete temporary file :{}", localPath);
            throw new IOException("Could not delete temporary file " + localPath);
        }
        logger.info("Cloning from {} to {}", remoteUrl, localPath);
        UsernamePasswordCredentialsProvider provider = new UsernamePasswordCredentialsProvider(username, password);
        try (Git git = Git.cloneRepository().setCredentialsProvider(provider).setBranch("master").setURI(remoteUrl).setDirectory(localPath).call()) {
            Repository repository = git.getRepository();
            // 构造差异数
            AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, revision);
            Ref head = repository.exactRef("refs/heads/master");
            String headVersionId = head.getObjectId().getName();
            logger.info("old version :{} ---- head version: {}", revision, headVersionId);
            AbstractTreeIterator newTreeParser = prepareTreeParser(repository, headVersionId);
            List<DiffEntry> diffEntries = git.diff().setOldTree(newTreeParser).setNewTree(oldTreeParser).call();
            diffEntries.stream().forEach(diffEntry -> {
                logger.info("{} , from: {} , to: {}", diffEntry, diffEntry.getOldId(), diffEntry.getNewId());
            });
            if (diffEntries.size() == 0) {
                logger.info("回滚版本与最新版本无任何差异，不需要回滚");
            } else {
                List<String> otherFiles = new ArrayList<>();
                // 取出需要回滚的文件，新增的文件不回滚
                for (DiffEntry diffEntry : diffEntries) {
                    if (diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                        git.rm().setCached(true).addFilepattern(diffEntry.getOldPath()).call();
                    } else {
                        otherFiles.add(diffEntry.getNewPath());
                    }
                }
                // checkout操作会丢失工作区的数据，暂存区和工作区的数据会恢复到指定（revision）的版本内容
                CheckoutCommand checkoutCmd = git.checkout();
                // 再次执行"git add"将覆盖暂存区的内容。
                checkoutCmd.addPaths(otherFiles);
                checkoutCmd.setStartPoint(revision);
                checkoutCmd.call();
                // 重新提交一次
                CommitCommand commitCmd = git.commit();
                String remark = "rollback to " + revision;
                commitCmd.setCommitter("git-svn-demo", "986619781@qq.com").setMessage(remark).call();
                git.push().setCredentialsProvider(provider).setForce(true).call();
            }
        }
        logger.info("rollback done...");
        FileUtils.deleteDirectory(localPath);
    }

    /**
     * 构造版本树 <br>
     * 
     * @Title: prepareTreeParser
     * @author LiHaiQing
     * @param: [repository, objectId]
     */
    private AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(ObjectId.fromString(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        }
    }

    public static void main(String[] args) {

    }
}
