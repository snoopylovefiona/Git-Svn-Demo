package com.snoopy.subversion.handler;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;

/**
 * @ClassName: ConflictResolverHandler
 * @Description: 冲突解决
 * @author: LiHaiQing
 * @date: 2018年8月22日 上午9:11:39
 */
public class ConflictResolverHandler implements ISVNConflictHandler {

    @Override
    public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {

        SVNConflictReason reason = conflictDescription.getConflictReason();
        SVNMergeFileSet mergeFileSet = conflictDescription.getMergeFiles();

        System.out.println("Conflict discovered in:" + mergeFileSet.getWCFile());
        System.out.println(reason);
        SVNConflictChoice choice = SVNConflictChoice.POSTPONE;
        /**
         * <pre>
         * System.out.print("Select: (p) postpone, (mf) mine-full, (tf) theirs-full ");
         * Scanner reader = new Scanner(System.in);
         * if (reader.hasNextLine()) {
         *     String sVNConflictChoice = reader.nextLine();
         *     if (sVNConflictChoice.equalsIgnoreCase("mf")) {
         *         choice = SVNConflictChoice.MINE_FULL;
         *     } else if (sVNConflictChoice.equalsIgnoreCase("tf")) {
         *         choice = SVNConflictChoice.THEIRS_FULL;
         *     }
         * }
         * </pre>
         */
        // 选择合并的文件
        choice = SVNConflictChoice.THEIRS_FULL;
        return new SVNConflictResult(choice, mergeFileSet.getResultFile());
    }
}
