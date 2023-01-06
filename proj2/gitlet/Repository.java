package gitlet;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import static gitlet.Utils.*;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /** TODO: add instance variables here.
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final String DEFAULT_BRANCH_NAME = "master";
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /*
     *   .gitlet
     *      |--objects
     *      |     |--commit and blob
     *      |--refs
     *      |    |--heads
     *      |         |--master
     *      |--stage
     *      |--HEAD
     */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File HEADS_DIR = join(REFS_DIR, "heads");

    //public static final File STAGE_FILE = join(GITLET_DIR, "stage");
    public static final File ADDSTAGE_FILE = join(GITLET_DIR, "add_stage");
    public static final File REMOVESTAGE_FILE = join(GITLET_DIR, "remove_stage");
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");

    /** TODO:维护curCommit、stage等变量，在Repository类初始化时，自动加载
     * 从而可以方便的进行操作、清空和持久化
     */

    public static void checkIfInitialized() {
        if (!GITLET_DIR.isDirectory()) {
            exit("Not in an initialized Gitlet directory.");
        }
    }

    /** Command init 初始化仓库 */
    public static void init(){
        if(GITLET_DIR.exists() && GITLET_DIR.isDirectory()){
            exit("A Gitlet version-control system already exists in the current directory.");
        }
        // 创建目录
        createInitDir();

        // 创建初始commit对象并存储进objects, 并在refs/head中创建branch头
        initCommitAndHeads();

        // 更新当前HEAD指向为当前分支
        initHEAD();
    }

    /** create dir */
    private static void createInitDir() {
        // Need to pay attention to the order
        try{
            mkdir(GITLET_DIR);
            mkdir(OBJECTS_DIR);
            mkdir(REFS_DIR);
            mkdir(HEADS_DIR);
            //mkdir(ADDSTAGE_FILE);   文件而不是目录
            //mkdir(REMOVESTAGE_FILE);
        }catch (Exception e) {
            exit(e.getMessage());
        }
    }

    private static void initCommitAndHeads(){
        Commit initCommit = new Commit();
        initCommit.persist(OBJECTS_DIR);

        setOrCreateBranch(DEFAULT_BRANCH_NAME, initCommit.getID());
    }

    /** set branch and its head, if branch not existed, created it */
    private static void setOrCreateBranch(String branchName, String commitID){
        File HEADS_FILE = join(HEADS_DIR, branchName);
        writeContents(HEADS_FILE, commitID);
    }

    private static void deleteBranch(String branchName) {
        File branchHead = join(HEADS_DIR, branchName);
        restrictedDelete(branchHead);
    }

    private static void initHEAD(){
        writeContents(HEAD_FILE, DEFAULT_BRANCH_NAME);
    }

    /** command add[filename]
     * TODO: support multi files
     * */
    public static void add(String fileName) {
        File file = getFileFromCWD(fileName);
        if (!file.exists()) {
            exit("File does not exist.");
        }
        // 创建blob对象
        Blob blob = new Blob(file);
        // 将blob对象持久化，并检查更新暂存区
        addBlob(file, blob);
    }

    private static File getFileFromCWD(String fileName) {
        return Paths.get(fileName).isAbsolute()   // path may out of CWD
                ? new File(fileName)
                : join(CWD, fileName);
    }

    public static void addBlob(File file, Blob blob){
        String filePath = file.getPath();
        String blobID = blob.getID();
        Stage addStage = loadAddStage();
        Stage removeStage = loadRemoveStage();
        Commit curCommit = loadCurCommit();

        // 如果该blobid和当前commit跟踪的blobid一模一样（即工作区的该文件相对于最近commit没更改任何内容），
        // 则不添加进addstage以免重复commit该文件
        // 同时如果stage里有它，则将其移出stage
        if(curCommit.isTrackedSameBlob(filePath, blobID)){
            // TODO: 优化代码逻辑
            // return null if filePath not existed
            addStage.delete(filePath);
            removeStage.delete(filePath);
        }else{
            // 走到这里说明当前blob没有被commit
            // 那么，如果blob的文件名和addstage里某个blob一样，则覆盖它的索引，否则直接新建添加进去
            // （例如先修改文件，add，再修改同一文件，再add，此时应该只暂存第二版，并且会产生一个不可达的blob对象）
            // TODO: 合并代码逻辑,其实可以不判断，直接map.put

            // may overwrite if filepath existed
            addStage.update(filePath, blobID);
            blob.persist(OBJECTS_DIR);
        }

        addStage.persist(ADDSTAGE_FILE);
        removeStage.persist(REMOVESTAGE_FILE);
    }

    private static Stage loadAddStage(){
        if (!ADDSTAGE_FILE.exists()) {
            return new Stage();
        }
        return readObject(ADDSTAGE_FILE, Stage.class);
    }

    private static Stage loadRemoveStage(){
        if (!REMOVESTAGE_FILE.exists()) {
            return new Stage();
        }
        return readObject(REMOVESTAGE_FILE, Stage.class);
    }

    private static Commit loadCurCommit() {
        String currCommitID = readCurCommitID();
        File CURR_COMMIT_FILE = join(OBJECTS_DIR, currCommitID);
        return readObject(CURR_COMMIT_FILE, Commit.class);
    }

    private static Commit loadCommitByID(String commitID){
        if (commitID.length() == 40) {
            File CUR_COMMIT_FILE = join(OBJECTS_DIR, commitID);
            if (!CUR_COMMIT_FILE.exists()) {
                return null;
            }
            return readObject(CUR_COMMIT_FILE, Commit.class);
        } else {
            List<String> objectID = plainFilenamesIn(OBJECTS_DIR);
            if(objectID != null) {
                for (String o : objectID) {
                    if (commitID.equals(o.substring(0, commitID.length()))) {
                        return readObject(join(OBJECTS_DIR, o), Commit.class);
                    }
                }
            }
            return null;
        }
    }

    private static Blob loadBlobByID(String blobID) {
        if (blobID.length() == 40) {
            File CUR_BLOB_FILE = join(OBJECTS_DIR, blobID);
            if (!CUR_BLOB_FILE.exists()) {
                return null;
            }
            return readObject(CUR_BLOB_FILE, Blob.class);
        } else {
            List<String> objectID = plainFilenamesIn(OBJECTS_DIR);
            if (objectID != null) {
                for (String o : objectID) {
                    if (blobID.equals(o.substring(0, blobID.length()))) {
                        return readObject(join(OBJECTS_DIR, o), Blob.class);
                    }
                }
            }
            return null;
        }
    }


    private static String readCurCommitID() {
        String curBranch = readCurBranchName();
        return readBranchHead(curBranch);
    }

    private static String readCurBranchName() {
        return readContentsAsString(HEAD_FILE);
    }

    public static String readBranchHead(String branchName){
        File HEADS_FILE = join(HEADS_DIR, branchName);
        return readContentsAsString(HEADS_FILE);
    }

    private static void setCurBranchHeadTo(String commitID){
        String curBranchName = getCurBranchName();
        setOrCreateBranch(curBranchName, commitID);
    }

    public static void setBranchTo(String targetBranchName){
        writeContents(HEAD_FILE, targetBranchName);
    }


    public static void commit(String message){
        // 检查message是否非空
        if(message.length() == 0){
            exit("Please enter a commit message.");
        }

        Stage addStage = loadAddStage();
        Stage removeStage = loadRemoveStage();

        // 检查暂存区是否非空
        if(!checkIfStageChanged(addStage, removeStage)){
            exit("No changes added to the commit.");
        }

        Commit preCommit = loadCurCommit();

        // 创建新commit对象并继承父亲的数据，并指向父亲
        // 根据stage修改
        // TODO：有必要新建commit对象吗，直接修改父commit对象是否会破坏commit类的设计？
        Commit newCommit = generateNewCommit(message, preCommit, addStage, removeStage);

        // 保存commit对象
        newCommit.persist(OBJECTS_DIR);

        // 更新当前branch的head为新commit
        setCurBranchHeadTo(newCommit.getID());

        // 清空并持久化暂存区
        clearStage(addStage, removeStage);
        persistStage(addStage, removeStage);
    }

    private static boolean checkIfStageChanged(Stage addStage, Stage removeStage){
        return !addStage.isEmpty() || !removeStage.isEmpty();
    }

    private static Commit generateNewCommit(String message, Commit preCommit, Stage addStage, Stage removeStage){
        Map<String, String> newPathToBlob = generateNewPathToBlob(addStage, removeStage, preCommit.getPathToBlobID());
        List<String> newParents = generateNewParents(preCommit);
        return new Commit(message, newPathToBlob, newParents);
    }

    private static Map<String, String> generateNewPathToBlob(Stage addStage, Stage removeStage, Map<String, String> pathToBlob){
        Map<String, String> addIndex = addStage.getIndex();
        Map<String, String> removeIndex = removeStage.getIndex();
        // TODO:这里并没有深拷贝一份pathToBlob，而是直接改
        for(String filePath : addIndex.keySet()){
            pathToBlob.put(filePath, addIndex.get(filePath));
        }
        for(String filePath : removeIndex.keySet()){
            pathToBlob.remove(filePath);
        }
        return pathToBlob;
    }

    private static List<String> generateNewParents(Commit preCommit){
        List<String> newParents = new ArrayList<>();
        newParents.add(preCommit.getID());
        return newParents;
    }

    private static String getCurBranchName(){
        return readContentsAsString(HEAD_FILE);
    }

    private static void clearStage(Stage addStage, Stage removeStage){
        addStage.clear();
        removeStage.clear();
    }

    private static void persistStage(Stage addStage, Stage removeStage){
        addStage.persist(ADDSTAGE_FILE);
        removeStage.persist(REMOVESTAGE_FILE);
    }

    public static void rm(String fileName) {
        File file = getFileFromCWD(fileName);
        String filePath = file.getPath();
        Stage addStage = loadAddStage();
        Commit curCommit = loadCurCommit();

        // 如果添加到了暂存区，则从当前暂存区删除
        if(addStage.isIndexedFile(filePath)){
            addStage.delete(filePath);
            addStage.persist(ADDSTAGE_FILE);
        }else if(curCommit.isTrackedFile(filePath)){
            // 如果文件被当前commit跟踪，则将其跟踪的filepath:boloID版本移入removestage
            // 并从CWD删除当前的文件
            Stage removeStage = loadRemoveStage();
            String removeBlobID = getBlobIDFromCurCommit(curCommit, filePath);
            removeStage.add(filePath, removeBlobID);
            removeStage.persist(REMOVESTAGE_FILE);
            // 删除失败会返回false
            restrictedDelete(file);
        }else{
            exit("No reason to remove the file.");
        }
    }

    private static String getBlobIDFromCurCommit(Commit curCommit, String filePath){
        return curCommit.getBlobIDOf(filePath);
    }


    public static void log(){
        Commit curCommit = loadCurCommit();
        List<String> logInfo = new ArrayList<>();
        while(!isInitCommit(curCommit)){
            logInfo.addAll(generateCommitInfo(curCommit));
            curCommit = getFirstParentCommit(curCommit);
        }
        logInfo.addAll(generateCommitInfo(curCommit));
        printLog(logInfo);
    }

    private static void printLog(List<String> logInfo){
        for(String s : logInfo){
            System.out.println(s);
        }
    }

    private static List<String> wrapInfo(String topBanner, List<String> info){
        info.add(0, topBanner);
        info.add(" ");
        return info;
    }

    private static List<String> generateCommitInfo(Commit curCommit){
        return wrapInfo("===", curCommit.getInfo());
    }

    private static boolean isInitCommit(Commit commit){
        // TODO:如果允许分离HEAD，则通过父引用为空的判断是否还有效？
        return commit.getParents().size() == 0;
    }

    private static Commit getFirstParentCommit(Commit curCommit){
        // TODO:目前是选择第一个父亲（也即当初合并的base分支）进行回溯，
        // 后续可能需要添加额外命令参数支持整个树的回溯
        String firstParentID = curCommit.getParents().get(0);
        return loadCommitByID(firstParentID);
    }

    public static void global_log(){
        List<String> commitList = plainFilenamesIn(OBJECTS_DIR);
        if(commitList == null) return;

        List<String> logInfo = new ArrayList<>();
        for (String id : commitList) {
            try{
                Commit commit = readObject(join(OBJECTS_DIR, id), Commit.class);
                logInfo.addAll(generateCommitInfo(commit));
            }catch (Exception ignored){
            }
        }
        printLog(logInfo);
    }

    public static void find(String message){
        List<String> commitList = plainFilenamesIn(OBJECTS_DIR);
        if(commitList == null) return;
        List<String> logInfo = new ArrayList<>();
        for (String id : commitList) {
            try{
                Commit commit = readObject(join(OBJECTS_DIR, id), Commit.class);
                if(commit.getMessage().contains(message)){
                    logInfo.add(commit.getID());
                }
            }catch (Exception ignored){
            }
        }

        if(logInfo.size() == 0){
            exit("Found no commit with that message.");
        }else{
            printLog(logInfo);
        }
    }

    public static void status(){
        List<String> logInfo = new ArrayList<>();
        logInfo.addAll(listBranches());
        logInfo.addAll(listStages());
        logInfo.addAll(listUnStagedModificationsAndUntracked());
        printLog(logInfo);
    }

    private static List<String> listBranches(){
        List<String> info = new ArrayList<>();
        String curBranch = getCurBranchName();
        info.add("*" + curBranch);
        List<String> branchList = plainFilenamesIn(HEADS_DIR);
        if(branchList != null){
            for(String branch : branchList){
                if(!branch.equals(curBranch)){
                    info.add(branch);
                }
            }
        }
        wrapInfo("=== Branches ===", info);
        return info;
    }

    private static List<String> listStages(){
        List<String> addInfo = new ArrayList<>();
        Stage addStage = loadAddStage();
        if(!addStage.isEmpty()){
            addInfo.addAll(addStage.getIndexedFileNames());
        }
        wrapInfo("=== Staged Files ===", addInfo);

        List<String> rmInfo = new ArrayList<>();
        Stage removeStage = loadRemoveStage();
        if(!removeStage.isEmpty()){
            rmInfo.addAll(removeStage.getIndexedFileNames());
        }
        wrapInfo("=== Removed Files ===", rmInfo);

        addInfo.addAll(rmInfo);

        return addInfo;
    }

    private static List<String> listUnStagedModificationsAndUntracked(){
        Map<String, String> CWDFilePathToBlobID = getCWDFilePathToBlobID();
        Map<String, String> commitFilePathToBlobID = loadCurCommit().getPathToBlobID();
        Map<String, String> addStageIndex = loadAddStage().getIndex();
        Map<String, String> removeStageIndex = loadRemoveStage().getIndex();

        List<String> info1 = listUnStagedModifications(CWDFilePathToBlobID,
                                                      commitFilePathToBlobID,
                                                      addStageIndex,
                                                      removeStageIndex);
        List<String> info2 = listUntracked(CWDFilePathToBlobID,
                                            commitFilePathToBlobID,
                                            addStageIndex,
                                            removeStageIndex);
        info1.addAll(info2);
        return info1;
    }

    private static Map<String, String> getCWDFilePathToBlobID(){
        Map<String, String> CWDFilePathToBlobID = new TreeMap<>();
        List<String> CWDFilenames = plainFilenamesIn(CWD);
        if(CWDFilenames != null) {
            for (String filename : CWDFilenames) {
                File file = join(CWD, filename);
                Blob cur = new Blob(file);
                CWDFilePathToBlobID.put(file.getPath(), cur.getID());
            }
        }
        return CWDFilePathToBlobID;
    }

    /** A file in the working directory is “modified but not staged” if it is
     * 1. Tracked in the current commit, changed in the working directory, but not staged; or
     * 2. Staged for addition, but with different contents than in the working directory; or
     * 3. Staged for addition, but deleted in the working directory; or
     * 4. Not staged for removal, but tracked in the current commit and deleted from the working directory.
     * TODO:将判断转为调用commit和stage对象的isTracked方法和inIndexed方法
     *  */
    public static List<String> listUnStagedModifications(Map<String, String> CWDFilePathToBlobID,
                                                         Map<String, String> commitFilePathToBlobID ,
                                                         Map<String, String> addStageIndex ,
                                                         Map<String, String> removeStageIndex ){
        List<String> info = new ArrayList<>();

        for(String trackedFilePath : commitFilePathToBlobID.keySet()){
            String trackedBlobID = commitFilePathToBlobID.get(trackedFilePath);
            String CWDBlobID = CWDFilePathToBlobID.get(trackedFilePath);
            String addStageBlobID = addStageIndex.get(trackedFilePath);
            String rmStageBlobID = removeStageIndex.get(trackedFilePath);

            String trackedFileName = trackedFilePath.substring(trackedFilePath.lastIndexOf(File.separator)+1);
            // 1.
            if(CWDBlobID != null && !CWDBlobID.equals(trackedBlobID) && addStageBlobID == null){
                info.add(trackedFileName + " (modified)");
            }
            // 4.
            if(rmStageBlobID == null && CWDBlobID == null ){
                info.add(trackedFileName + " (deleted)");
            }
        }

        for(String addStagedFilePath : addStageIndex.keySet()){
            String addStagedBlobID = addStageIndex.get(addStagedFilePath);
            String CWDBlobID = CWDFilePathToBlobID.get(addStagedFilePath);

            String addStagedFileName = addStagedFilePath.substring(addStagedFilePath.lastIndexOf(File.separator)+1);
            // 3. 2.
            if(CWDBlobID == null ){
                info.add(addStagedFileName + " (deleted)");
            }else if(!addStagedBlobID.equals(CWDBlobID)){
                info.add(addStagedFileName + " (modified)");
            }
        }
        wrapInfo("=== Modifications Not Staged For Commit ===", info);
        return info;
    }

    /** The final category (“Untracked Files”) is for files
     *  present in the working directory but neither staged for addition nor tracked.
     *  This includes files that have been staged for removal, but then re-created without Gitlet’s knowledge.
     *  Ignore any subdirectories that may have been introduced, since Gitlet does not deal with them.
     *  TODO:将判断转为调用commit和stage对象的isTracked方法和inIndexed方法
     */
    private static List<String> listUntracked(Map<String, String> CWDFilePathToBlobID,
                                              Map<String, String> commitFilePathToBlobID ,
                                              Map<String, String> addStageIndex ,
                                              Map<String, String> removeStageIndex ){
        List<String> info = new ArrayList<>();

        for(String CWDFilePath : CWDFilePathToBlobID.keySet()){
            if(!addStageIndex.containsKey(CWDFilePath) && !commitFilePathToBlobID.containsKey(CWDFilePath)
                    || removeStageIndex.containsKey(CWDFilePath)){
                String CWDFileName = CWDFilePath.substring(CWDFilePath.lastIndexOf(File.separator)+1);
                info.add(CWDFileName);
            }
        }
        wrapInfo("=== Untracked Files ===", info);
        return info;
    }

    // checkout overload1
    public static void checkout(String fileName){
        String curCommitID = readCurCommitID();
        checkout(curCommitID, fileName);
    }

    // checkout overload2
    public static void checkout(String commitID, String fileName){
        Commit commit = loadCommitByID(commitID);
        if(commit == null) {
            exit("No commit with that id exists.");
        }else{
            File file = getFileFromCWD(fileName);
            checkIfCommitTrackedFile(commit, file);

            String blobID = commit.getBlobIDOf(file.getPath());
            Blob blob = loadBlobByID(blobID);

            // 这里会覆盖写吗?
            writeContents(file, blob.getContent());

            /*
            // unIndexed for stage
            Stage addStage = loadAddStage();
            Stage removeStage = loadRemoveStage();
            addStage.delete(file.getPath());
            removeStage.delete(file.getPath());
            persistStage(addStage, removeStage);
            *
             */
        }
    }

    public static void checkIfCommitTrackedFile(Commit commit, File file){
        if (!commit.isTrackedFile(file.getPath())){
            exit("File does not exist in that commit.");
        }
    }

    // checkout 3
    public static void checkoutBranch(String targetBranchName){
        checkIfBranchExisted(targetBranchName, false);
        checkIfIsCurBranch(targetBranchName, "No need to checkout the current branch.");
        checkIfCurBranchHasUntrackedFiles();

        // 切换分支
        setBranchTo(targetBranchName);

        // 将工作区的文件内容覆盖为目标分支的head的所有文件
        reset(readCurCommitID());
    }


    private static void checkIfCurBranchHasUntrackedFiles() {
        Commit curCommit = loadCurCommit();
        // TODO:增加对多层目录的文件检测，目前只是在根目录
        // TODO:目前gitlet的实现是不比较stage，只考虑commmit是否tracked，并且也不比较tracked的blobID
        //  真正的git如果发现有stage没有commit会拒绝
        Map<String, String> CWDFilePathToBlobID = getCWDFilePathToBlobID();
        for(String CWDFilePath : CWDFilePathToBlobID.keySet()){
            if(!curCommit.isTrackedFile(CWDFilePath)){
                exit("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
    }


    public static void branch(String branchName){
        checkIfBranchExisted(branchName, true);
        Commit curCommit = loadCurCommit();
        setOrCreateBranch(branchName, curCommit.getID());
    }

    public static void rm_branch(String branchName){
        checkIfBranchExisted(branchName, false);
        checkIfIsCurBranch(branchName, "Cannot remove the current branch.");
        deleteBranch(branchName);
    }

    private static boolean isBranchExisted(String branchName){
        List<String> allBranches = plainFilenamesIn(HEADS_DIR);
        return allBranches != null && allBranches.contains(branchName);
    }
    private static void checkIfBranchExisted(String branchName, boolean createNew) {
        if (createNew && isBranchExisted(branchName)) {
            exit("A branch with that name already exists.");
        }else if(!createNew && !isBranchExisted(branchName)){
            exit("No such branch exists.");
        }
    }
    private static void checkIfIsCurBranch(String branchName, String errMessage){
        if(branchName.equals(getCurBranchName())){
            exit(errMessage);
        }
    }

    /** Checks out all the files tracked by the given commit.
     *  Removes tracked files that are not present in that commit.
     *  Also moves the current branch’s head to that commit node.
     */
    public static void reset(String targetCommitID){
        // TODO:这里实现的是--hard模式，后续可以支持--soft和--mixed模式
        Commit targetCommit = loadCommitByID(targetCommitID);
        if(targetCommit == null){
            exit("No commit with that id exists.");
        }else{
            checkIfCurBranchHasUntrackedFiles();

            // deleted files that commit tracked but targetCommit untracked
            deleteTargetCommitUntrackedFiles(targetCommit);

            // overwrite files with targetCommit
            overwriteCWDFilesWith(targetCommit);

            // cleared stage
            clearStageAndPersist();

            // switch head to target
            setCurBranchHeadTo(targetCommitID);
        }
    }

    private static void deleteTargetCommitUntrackedFiles(Commit targetCommit){
        Commit curCommit = loadCurCommit();
        Map<String, String> curCommitFilePathToBlobID = curCommit.getPathToBlobID();
        for(String filePath : curCommitFilePathToBlobID.keySet()){
            if(!targetCommit.isTrackedFile(filePath)){
                restrictedDelete(filePath);
            }
        }
    }

    private static void overwriteCWDFilesWith(Commit targetCommit){
        Commit curCommit = loadCurCommit();
        Map<String, String> targetCommitFilePathToBlobID = targetCommit.getPathToBlobID();
        for(String filePath : targetCommitFilePathToBlobID.keySet()){
            if(curCommit.isTrackedSameBlob(filePath, curCommit.getBlobIDOf(filePath))) continue;
            File file = getFileFromCWD(filePath);
            Blob blob = loadBlobByID(targetCommitFilePathToBlobID.get(filePath));
            writeContents(file, blob.getContent());
        }
    }

    private static void clearStageAndPersist(){
        Stage addStage = loadAddStage();
        addStage.clear();
        addStage.persist(ADDSTAGE_FILE);
        Stage removeStage = loadRemoveStage();
        removeStage.clear();
        removeStage.persist(REMOVESTAGE_FILE);
    }




}
