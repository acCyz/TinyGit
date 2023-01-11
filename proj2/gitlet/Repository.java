package gitlet;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final String DEFAULT_BRANCH_NAME = "master";
    private final File CWD;
    /** The .gitlet directory. */
    /*
     *   .gitlet
     *      |--objects
     *      |     |--commit and blob
     *      |--refs
     *      |    |--heads
     *      |         |--master
     *      |    |--remotes
     *      |         |--orig1
     *      |              |--HEAD(cur branch of orig1)
     *      |              |--main(branch heads)
     *      |         |--orig2
     *      |--stage
     *      |--HEAD (current local head)
     *      |--FETCH_HEAD (latest commitID of fetched remote branches)
     *      |--ORIG_HEAD (the LCA commitID of HEAD and remote)
     */
    private File GITLET_DIR;
    private File OBJECTS_DIR;
    private File REFS_DIR;
    private File HEADS_DIR;
    private File REMOTES_DIR;
    private File ADDSTAGE_FILE;
    private File REMOVESTAGE_FILE;
    private File CONFIG_FILE;
    private File HEAD_FILE;

    /** TODO:维护curCommit、stage等变量，在Repository类初始化时，自动加载
     * 从而可以方便的进行操作、清空和持久化
     */
    public Repository(){
        CWD = new File(System.getProperty("user.dir"));
        setDirs();
    }
    public Repository(String cwd){
        CWD = new File(cwd);
        setDirs();
    }

    /*
     *   .gitlet
     *      |--objects
     *      |     |--commit and blob
     *      |--refs
     *      |    |--heads
     *      |         |--master
     *      |    |--remotes
     *      |         |--orig1
     *      |              |--HEAD(cur branch of orig1)
     *      |              |--main(branch heads)
     *      |         |--orig2
     *      |--stage
     *      |--HEAD (current local head)
     *      |--FETCH_HEAD (latest commitID of fetched remote branches)
     *      |--ORIG_HEAD (the LCA commitID of HEAD and remote)
     */
    private void setDirs(){
        this.GITLET_DIR = join(CWD, ".gitlet");
        this.OBJECTS_DIR = join(GITLET_DIR, "objects");
        this.REFS_DIR = join(GITLET_DIR, "refs");
        this.HEADS_DIR = join(REFS_DIR, "heads");
        this.REMOTES_DIR = join(REFS_DIR, "remotes");
        this.ADDSTAGE_FILE = join(GITLET_DIR, "add_stage");
        this.REMOVESTAGE_FILE = join(GITLET_DIR, "remove_stage");
        this.CONFIG_FILE = join(GITLET_DIR, "config");;
        this.HEAD_FILE = join(GITLET_DIR, "HEAD");
    }

    public void checkIfInitialized(String errMessage) {
        if (!GITLET_DIR.isDirectory()) {
            exit(errMessage);
        }
    }

    /** Command init 初始化仓库 */
    public void init(){
        if (GITLET_DIR.exists() && GITLET_DIR.isDirectory()){
            exit("A Gitlet version-control system already exists in the current directory.");
        }
        // 创建目录
        createInitDir();

        // 创建初始commit对象并存储进objects, 并在refs/head中创建branch头
        initCommitAndHeads();

        // 更新当前HEAD指向为当前分支
        initHEAD();

        initConfig();
    }

    /** create dir */
    private void createInitDir() {
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

    private void initCommitAndHeads(){
        Commit initCommit = new Commit();
        initCommit.persist(OBJECTS_DIR);

        setOrCreateBranch(DEFAULT_BRANCH_NAME, initCommit.getID());
    }

    /** set branch and its head, if branch not existed, created it */
    private void setOrCreateBranch(String branchName, String commitID){
        File HEADS_FILE = join(HEADS_DIR, branchName);
        writeContents(HEADS_FILE, commitID);
    }

    private void deleteBranch(String branchName) {
        File branchHead = join(HEADS_DIR, branchName);
        deleteGitLetFile(branchHead);
    }

    private void initHEAD(){
        writeContents(HEAD_FILE, DEFAULT_BRANCH_NAME);
    }
    private void initConfig(){
        writeContents(CONFIG_FILE, "");
    }

    /** command add[filename]
     * TODO: support multi files
     * */
    public void add(String fileName) {
        File file = getFileFromCWD(fileName);
        if (!file.exists()) {
            exit("File does not exist.");
        }
        // 创建blob对象
        Blob blob = new Blob(file);
        // 将blob对象持久化，并检查更新暂存区
        addBlob(file, blob);
    }

    private File getFileFromCWD(String fileName) {
        return Paths.get(fileName).isAbsolute()   // path may out of CWD
                ? new File(fileName)
                : join(CWD, fileName);
    }

    public void addBlob(File file, Blob blob){
        String filePath = getRelativePath(file);
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

    private void addConfig(String remoteName, String remoteAddress) {
        String contents = readContentsAsString(CONFIG_FILE);
        contents += "[remote \"" + remoteName + "\"]\n";
        contents += remoteAddress + "\n";
        setConfig(contents);
    }

    private void rmConfig(String remoteName) {
        String[] contents = readContentsAsString(CONFIG_FILE).split("\n");
        String target = "[remote \"" + remoteName + "\"]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contents.length;) {
            if (contents[i].equals(target)) {
                // skip content
                i += 2;
            } else {
                sb.append(contents[i]);
            }
        }
        setConfig(sb.toString());
    }

    private void setConfig(String contents) {
        writeContents(CONFIG_FILE, contents);
    }

    private Stage loadAddStage(){
        if (!ADDSTAGE_FILE.exists()) {
            return new Stage();
        }
        return readObject(ADDSTAGE_FILE, Stage.class);
    }

    private Stage loadRemoveStage(){
        if (!REMOVESTAGE_FILE.exists()) {
            return new Stage();
        }
        return readObject(REMOVESTAGE_FILE, Stage.class);
    }

    private Commit loadCurCommit() {
        String curCommitID = readCurCommitID();
        File curCommitFile = join(OBJECTS_DIR, curCommitID);
        return readObject(curCommitFile, Commit.class);
    }

    private Commit loadCommitByID(String commitID){
        if (commitID.length() == 40) {
            File curCommitFile = join(OBJECTS_DIR, commitID);
            if (!curCommitFile.exists()) {
                return null;
            }
            return readObject(curCommitFile, Commit.class);
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

    private Blob loadBlobByID(String blobID) {
        if (blobID.length() == 40) {
            File curBlobFile = join(OBJECTS_DIR, blobID);
            if (!curBlobFile.exists()) {
                return null;
            }
            return readObject(curBlobFile, Blob.class);
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


    private String readCurCommitID() {
        String curBranch = readCurBranchName();
        return readBranchHead(curBranch);
    }

    private String readCurBranchName() {
        return readContentsAsString(HEAD_FILE);
    }

    public String readBranchHead(String branchName) {
        File headsFile = null;
        String[] branches = branchName.split(File.separator);
        if (branches.length == 1) {
            headsFile = join(HEADS_DIR, branchName);
        } else if (branches.length == 2) {
            headsFile = join(REMOTES_DIR, branches[0], branches[1]);
        }
        return readContentsAsString(headsFile);
    }

    private void setCurBranchHeadTo(String commitID) {
        String curBranchName = getCurBranchName();
        setOrCreateBranch(curBranchName, commitID);
    }

    public void setBranchTo(String targetBranchName){
        writeContents(HEAD_FILE, targetBranchName);
    }

    public void commit(String message) {
        // 检查message是否非空
        if(message.length() == 0){
            exit("Please enter a commit message.");
        }

        Stage addStage = loadAddStage();
        Stage removeStage = loadRemoveStage();

        // 检查暂存区是否非空
        checkIfStageChanged(addStage, removeStage);

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

    private void checkIfStageChanged(Stage addStage, Stage removeStage){
        if(addStage.isEmpty() && removeStage.isEmpty()){
            exit("No changes added to the commit.");
        }
    }

    private Commit generateNewCommit(String message, Commit preCommit, Stage addStage, Stage removeStage){
        Map<String, String> newPathToBlob = generateNewPathToBlob(addStage, removeStage, preCommit.getPathToBlobID());
        List<String> newParents = generateNewParents(preCommit);
        return new Commit(message, newPathToBlob, newParents);
    }

    private Map<String, String> generateNewPathToBlob(Stage addStage, Stage removeStage, Map<String, String> pathToBlob){
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

    private List<String> generateNewParents(Commit preCommit){
        List<String> newParents = new ArrayList<>();
        newParents.add(preCommit.getID());
        return newParents;
    }

    private String getCurBranchName(){
        return readContentsAsString(HEAD_FILE);
    }

    private void clearStage(Stage addStage, Stage removeStage){
        addStage.clear();
        removeStage.clear();
    }

    private void persistStage(Stage addStage, Stage removeStage){
        addStage.persist(ADDSTAGE_FILE);
        removeStage.persist(REMOVESTAGE_FILE);
    }

    public void rm(String fileName) {
        File file = getFileFromCWD(fileName);
        String filePath = getRelativePath(file);
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
            String removeBlobID = curCommit.getBlobIDOf(filePath);
            removeStage.add(filePath, removeBlobID);
            removeStage.persist(REMOVESTAGE_FILE);
            // 删除失败会返回false
            restrictedDelete(file);
        }else{
            exit("No reason to remove the file.");
        }
    }

    private void printLog(List<String> logInfo){
        for(String s : logInfo){
            System.out.println(s);
        }
    }

    public void log(){
        Commit curCommit = loadCurCommit();
        List<String> logInfo = new ArrayList<>();
        while(!isInitCommit(curCommit)){
            logInfo.addAll(generateCommitInfo(curCommit));
            curCommit = getFirstParentCommit(curCommit);
        }
        logInfo.addAll(generateCommitInfo(curCommit));
        printLog(logInfo);
    }

    private List<String> wrapInfo(String topBanner, List<String> info){
        info.add(0, topBanner);
        info.add(" ");
        return info;
    }

    private List<String> generateCommitInfo(Commit curCommit){
        return wrapInfo("===", curCommit.getInfo());
    }

    private boolean isInitCommit(Commit commit){
        // TODO:如果允许分离HEAD，则通过父引用为空的判断是否还有效？
        return commit.isNoParent();
    }

    private Commit getFirstParentCommit(Commit curCommit){
        // TODO:目前是选择第一个父亲（也即当初合并的base分支）进行回溯，
        // 后续可能需要添加额外命令参数支持整个树的回溯
        String firstParentID = curCommit.getParents().get(0);
        return loadCommitByID(firstParentID);
    }

    public void global_log(){
        List<String> commitList = plainFilenamesIn(OBJECTS_DIR);
        if (commitList == null) {
            return;
        }

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

    public void find(String message){
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

    public void status(){
        List<String> logInfo = new ArrayList<>();
        logInfo.addAll(listBranches());
        logInfo.addAll(listStages());
        logInfo.addAll(listUnStagedModificationsAndUntracked());
        printLog(logInfo);
    }

    private List<String> listBranches(){
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

    private List<String> listStages(){
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

    private List<String> listUnStagedModificationsAndUntracked(){
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

    private Map<String, String> getCWDFilePathToBlobID(){
        Map<String, String> CWDFilePathToBlobID = new TreeMap<>();
        List<String> CWDFilenames = plainFilenamesIn(CWD);
        if(CWDFilenames != null) {
            for (String filename : CWDFilenames) {
                File file = getFileFromCWD(filename);
                Blob curCWDBlob = new Blob(file);
                CWDFilePathToBlobID.put(getRelativePath(file), curCWDBlob.getID());
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
    public List<String> listUnStagedModifications(Map<String, String> CWDFilePathToBlobID,
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
    private List<String> listUntracked(Map<String, String> CWDFilePathToBlobID,
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
    public void checkout(String fileName){
        String curCommitID = readCurCommitID();
        checkout(curCommitID, fileName);
    }

    // checkout overload2
    public void checkout(String commitID, String fileName){
        Commit commit = loadCommitByID(commitID);
        if(commit == null) {
            exit("No commit with that id exists.");
        }else{
            File file = getFileFromCWD(fileName);
            checkIfCommitTrackedFile(commit, file);

            String blobID = commit.getBlobIDOf(getRelativePath(file));
            Blob blob = loadBlobByID(blobID);

            // 这里会覆盖写吗?
            writeContents(file, blob.getContent());

        }
    }

    public void checkIfCommitTrackedFile(Commit commit, File file){
        if (!commit.isTrackedFile(getRelativePath(file))){
            exit("File does not exist in that commit.");
        }
    }

    // checkout 3
    public void checkoutBranch(String targetBranchName){
        checkIfBranchNotExisted(targetBranchName, "No such branch exists.");
        checkIfIsCurBranch(targetBranchName, "No need to checkout the current branch.");

        String targetCommitID = readBranchHead(targetBranchName);
        Commit targetCommit = loadCommitByID(targetCommitID);
        checkIfHasUntrackedFilesWillOverwriteBy(targetCommit);

        // 将当前分支头的文件内容覆盖为目标分支的head的所有文件
        changeCWDAndClearStageTo(targetCommit);

        // 将当前分支指针指向目标分支
        setBranchTo(targetBranchName);
    }

    private void changeCWDAndClearStageTo(Commit targetCommit){
        // deleted files that commit tracked but targetCommit untracked
        deleteTargetCommitUntrackedCWDFiles(targetCommit);

        // overwrite files with targetCommit
        overwriteCWDFilesWith(targetCommit);

        // cleared stage
        clearStageAndPersist();
    }

    private void deleteTargetCommitUntrackedCWDFiles(Commit targetCommit){
        Commit curCommit = loadCurCommit();
        Map<String, String> curCommitFilePathToBlobID = curCommit.getPathToBlobID();
        for(String filePath : curCommitFilePathToBlobID.keySet()){
            if(!targetCommit.isTrackedFile(filePath)){
                File file = getFileFromCWD(filePath);
                restrictedDelete(file);
            }
        }
    }

    // TODO:用map交集操作来寻找两个commit共同追踪的部分
    private void overwriteCWDFilesWith(Commit targetCommit){
        Commit curCommit = loadCurCommit();
        Map<String, String> targetCommitFilePathToBlobID = targetCommit.getPathToBlobID();
        for(String targetFilePath : targetCommitFilePathToBlobID.keySet()){
            String targetBlobID = targetCommit.getBlobIDOf(targetFilePath);
            if(curCommit.isTrackedSameBlob(targetFilePath, targetBlobID)) continue;
            File file = getFileFromCWD(targetFilePath);
            Blob targetBlob = loadBlobByID(targetCommitFilePathToBlobID.get(targetFilePath));
            writeContents(file, targetBlob.getContent());
        }
    }

    private void clearStageAndPersist(){
        Stage addStage = loadAddStage();
        addStage.clear();
        addStage.persist(ADDSTAGE_FILE);
        Stage removeStage = loadRemoveStage();
        removeStage.clear();
        removeStage.persist(REMOVESTAGE_FILE);
    }

    /** 1. Untracked Files is for files present in the working directory but neither staged for addition nor tracked.
     *  This includes files that have been staged for removal, but then re-created without Gitlet’s knowledge.
     *
     *  2. If a working file is untracked in the current branch **【and】** would be overwritten by the checkout/reset
     *  */
    private void checkIfHasUntrackedFilesWillOverwriteBy(Commit targetCommit) {
        Commit curCommit = loadCurCommit();
        // TODO:增加对多层目录的文件检测，目前只是在根目录
        Map<String, String> CWDFilePathToBlobID = getCWDFilePathToBlobID();
        Stage addStage = loadAddStage();
        Stage removeStage = loadRemoveStage();
        for(String CWDFilePath : CWDFilePathToBlobID.keySet()){
            // 1.
            if(! addStage.isIndexedFile(CWDFilePath) && !curCommit.isTrackedFile(CWDFilePath)
                    || removeStage.isIndexedFile(CWDFilePath)){
                String CWDFileBlobID = CWDFilePathToBlobID.get(CWDFilePath);
                // 2.
                if(targetCommit.willOverwrite(CWDFilePath, CWDFileBlobID)){
                    exit("There is an untracked file in the way; delete it, or add and commit it first.");
                }
            }
        }
    }

    public void branch(String branchName){
        checkIfBranchExisted(branchName);
        Commit curCommit = loadCurCommit();
        setOrCreateBranch(branchName, curCommit.getID());
    }

    public void rm_branch(String branchName){
        checkIfBranchNotExisted(branchName, "A branch with that name does not exist.");
        checkIfIsCurBranch(branchName, "Cannot remove the current branch.");
        deleteBranch(branchName);
    }

    private boolean isBranchExisted(String branchName){
        File file = null;
        String[] branches = branchName.split(File.separator);
        if (branches.length == 1) {
            file = join(HEADS_DIR, branchName);
        } else if (branches.length == 2) {
            file = join(REMOTES_DIR, branches[0], branches[1]);
        }
        return file != null && file.exists();
    }


    private void checkIfBranchExisted(String branchName) {
        if (isBranchExisted(branchName)) {
            exit("A branch with that name already exists.");
        }
    }
    /** 因为测试用例严格规定了不同的错误输出语句，
     *  因此只能从checkIfBranchExisted拆出来写check并输出 */
    private void checkIfBranchNotExisted(String branchName, String errMessage){
        if(!isBranchExisted(branchName)){
            exit(errMessage);
        }
    }

    private void checkIfIsCurBranch(String branchName, String errMessage){
        if(branchName.equals(getCurBranchName())){
            exit(errMessage);
        }
    }

    /** Checks out all the files tracked by the given commit.
     *  Removes tracked files that are not present in that commit.
     *  Also moves the current branch’s head to that commit node.
     */
    public void reset(String targetCommitID){
        // TODO:这里实现的是--hard模式，后续可以支持--soft和--mixed模式
        Commit targetCommit = loadCommitByID(targetCommitID);
        if(targetCommit == null){
            exit("No commit with that id exists.");
        }else{
            checkIfHasUntrackedFilesWillOverwriteBy(targetCommit);

            changeCWDAndClearStageTo(targetCommit);

            // switch head to target
            setCurBranchHeadTo(targetCommitID);
        }
    }

    public void merge(String otherBranchName){
        checkIfCurBranchHasUnCommitted();
        checkIfBranchNotExisted(otherBranchName, "A branch with that name does not exist.");
        checkIfIsCurBranch(otherBranchName, "Cannot merge a branch with itself.");

        Commit headCommit = loadCurCommit();
        Commit otherCommit = loadCommitByID(readBranchHead(otherBranchName));
        checkIfHasUntrackedFilesWillOverwriteBy(otherCommit);

        Commit splitCommit = findSplitPoint(headCommit, otherCommit);
        checkIfSplitIsOneOf(splitCommit, otherCommit, headCommit);

        List<String> allFiles = generateAllFiles(splitCommit, headCommit, otherCommit);
        Map<String, String> owFilePathToBlob = overwriteMergeFiles(allFiles, splitCommit, headCommit, otherCommit);
        Map<String, String> rmFilePathToBlob = deleteMergeFiles(allFiles, splitCommit, headCommit, otherCommit);
        Map<String, String> cfFilePathToBlob = mergeConflict(allFiles, splitCommit, headCommit, otherCommit);

        String message = "Merged " + otherBranchName + " into " + readCurBranchName() + ".";
        Map<String, String> newFilePathToBlob = mergeNewPathToBlob(headCommit.getPathToBlobID(), owFilePathToBlob, rmFilePathToBlob, cfFilePathToBlob);

        // TODO: 如果使用双括号初始化，则会序列化失败，因为创建的是List的匿名子类
        List<String> newParents = new ArrayList<>();
        newParents.add(headCommit.getID());
        newParents.add(otherCommit.getID());

        Commit newCommit = new Commit(message, newFilePathToBlob, newParents);
        // 保存commit对象
        newCommit.persist(OBJECTS_DIR);

        // 更新当前branch的head为新commit
        setCurBranchHeadTo(newCommit.getID());

        if(!cfFilePathToBlob.isEmpty()){
            exit("Encountered a merge conflict.");
        }
    }

    private void checkIfCurBranchHasUnCommitted(){
        if(!loadAddStage().isEmpty() || !loadRemoveStage().isEmpty()){
            exit("You have uncommitted changes.");
        }
    }

    private void checkIfSplitIsOneOf(Commit split, Commit other, Commit head){
        if(split.equals(other)){
            exit("Given branch is an ancestor of the current branch.");
        }else if(split.equals(head)){
            // take curBranchHead fast moved to other head
            // 将当前分支头的文件内容覆盖为目标分支的head的所有文件
            changeCWDAndClearStageTo(other);
            // 将当前分支头指针指向other
            setCurBranchHeadTo(other.getID());
            exit("Current branch fast-forwarded.");
        }
    }

    private Commit findSplitPoint(Commit head, Commit other){
        Map<String, Integer> headAncestors = findAncestors(head);
        Queue<Commit> queue = new ArrayDeque<>();
        queue.offer(other);
        while(!queue.isEmpty()){
            int size = queue.size();
            while(size > 0){
                Commit cur = queue.poll();
                if(headAncestors.containsKey(cur.getID())){
                    return cur;
                }
                if(!isInitCommit(cur)){
                    for(String parentID : cur.getParents()){
                        Commit parent = loadCommitByID(parentID);
                        queue.offer(parent);
                    }
                }
                size--;
            }
        }
        return new Commit();
    }

    private Map<String, Integer> findAncestors(Commit commit){
        Map<String, Integer> map = new TreeMap<>();
        Queue<Commit> queue = new ArrayDeque<>();
        queue.offer(commit);
        int depth = 1;
        while(!queue.isEmpty()){
            int size = queue.size();
            while(size > 0){
                Commit cur = queue.poll();
                // putIfAbsent, if the KEY cur is existed, prevent put multi initCommit
                map.putIfAbsent(cur.getID(), depth);
                if(!isInitCommit(cur)){
                    for(String parentID : cur.getParents()){
                        Commit parent = loadCommitByID(parentID);
                        queue.offer(parent);
                    }
                }
                size--;
            }
            depth ++;
        }
        return map;
    }

    private Map<String, String> mergeNewPathToBlob(Map<String, String> headFilePathToBlob,
                                                             Map<String, String> owFilePathToBlob,
                                                             Map<String, String> rmFilePathToBlob,
                                                             Map<String, String> cfFilePathToBlob){
        Map<String, String> newFilePathToBlob = new TreeMap<>(headFilePathToBlob);
        for(String owFilePath : owFilePathToBlob.keySet()){
            newFilePathToBlob.put(owFilePath, owFilePathToBlob.get(owFilePath));
        }
        for(String rmFilePath : rmFilePathToBlob.keySet()){
            newFilePathToBlob.remove(rmFilePath);
        }
        for(String cfFilePath : cfFilePathToBlob.keySet()){
            newFilePathToBlob.put(cfFilePath, cfFilePathToBlob.get(cfFilePath));
        }
        return newFilePathToBlob;
    }


    /**
     * 1. modified in other but not head, overwrite it with other
     * 5. neither in split nor head but in other, write it with other
     * @param split
     * @param head
     * @param other
     */
    private Map<String, String> overwriteMergeFiles(List<String> allFiles, Commit split, Commit head, Commit other){
        Map<String, String> splitMap = split.getPathToBlobID();
        Map<String, String> headMap = head.getPathToBlobID();
        Map<String, String> otherMap = other.getPathToBlobID();

        Map<String, String> overwriteList = new TreeMap<>();
        for(String filepath : allFiles){
            String splitVersion = splitMap.get(filepath);
            String headVersion = headMap.get(filepath);
            String otherVersion = otherMap.get(filepath);
            if((splitVersion != null && otherVersion != null && !splitVersion.equals(otherVersion) && splitVersion.equals(headVersion))
                    ||(splitVersion == null && headVersion == null && otherVersion != null)){
                overwriteList.put(filepath, otherVersion);
            }
        }

        // overwrite and write
        for(String filePath : overwriteList.keySet()){
            checkout(other.getID(), filePath);
        }

        return overwriteList;
    }

    /**
     * 3.2. modified in head and other in different ways, existed conflict
     * @param allFiles
     * @param split
     * @param head
     * @param other
     */
    private Map<String, String> mergeConflict(List<String> allFiles, Commit split, Commit head, Commit other){
        Map<String, String> splitMap = split.getPathToBlobID();
        Map<String, String> headMap = head.getPathToBlobID();
        Map<String, String> otherMap = other.getPathToBlobID();

        Map<String, String> conflictList = new TreeMap<>();
        for(String filePath : allFiles){
            String splitVersion = splitMap.get(filePath);
            String headVersion = headMap.get(filePath);
            String otherVersion = otherMap.get(filePath);

            if(!equal(splitVersion, headVersion) && !equal(splitVersion, otherVersion) && !equal(headVersion, otherVersion)){
                Blob nb = generateConflictBlob(headVersion, otherVersion);
                nb.persist(OBJECTS_DIR);
                File file = getFileFromCWD(filePath);
                writeContents(file, nb.getContent());

                conflictList.put(filePath, nb.getID());
            }
        }
        return conflictList;
    }

    private Blob generateConflictBlob(String headBlobID, String otherBlobID){
        String headContent = headBlobID == null ? "":new String(loadBlobByID(headBlobID).getContent(), StandardCharsets.UTF_8);
        String otherContent = otherBlobID == null ? "":new String(loadBlobByID(otherBlobID).getContent(), StandardCharsets.UTF_8);;
        String newContent = "<<<<<<< HEAD\n" +
                headContent+
                "=======\n" +
                otherContent +
                ">>>>>>>\n";
        return new Blob(newContent.getBytes(StandardCharsets.UTF_8));
    }


    /**
     * 6. unmodified in head but not present in other, remove it
     * @param allFiles
     * @param split
     * @param head
     * @param other
     */
    private Map<String, String> deleteMergeFiles(List<String> allFiles, Commit split, Commit head, Commit other) {
        Map<String, String> splitMap = split.getPathToBlobID();
        Map<String, String> headMap = head.getPathToBlobID();
        Map<String, String> otherMap = other.getPathToBlobID();

        Map<String, String> removeList = new TreeMap<>();
        for(String filePath : allFiles){
            String splitVersion = splitMap.get(filePath);
            String headVersion = headMap.get(filePath);
            String otherVersion = otherMap.get(filePath);
            if(splitVersion != null && splitVersion.equals(headVersion) && otherVersion == null){
                removeList.put(filePath, headVersion);
            }
        }
        for(String filePath : removeList.keySet()){
            File file = getFileFromCWD(filePath);
            restrictedDelete(file);
        }

        return removeList;
    }

    /**
     * do nothing for
     * 2. modified in head but not in other, overwrite with head
     * 3.1. modified in head and other, but still keep same content
     * 4. neither in split nor other but in head, write it
     * 7. unmodified in other but not present in head, (remain remove)
     */
    private List<String> generateAllFiles(Commit splitPoint, Commit newCommit, Commit mergeCommit) {
        List<String> allFiles = new ArrayList<>(splitPoint.getPathToBlobID().keySet());
        allFiles.addAll(newCommit.getPathToBlobID().keySet());
        allFiles.addAll(mergeCommit.getPathToBlobID().keySet());
        Set<String> set = new HashSet<>(allFiles);
        allFiles.clear();
        allFiles.addAll(set);
        return allFiles;
    }

    public void add_remote(String remoteName, String remoteAddress) {
        checkIfRemoteNameExisted(remoteName);
        // TODO:check user info and server address valid

        // java.io.File.separator
        String validAddress = remoteAddress.replaceAll("/", File.separator);
        // do not mkdirs in add_remote
        /*
         * same as git
        [remote "origin"]
            url = ..\\remotegit\\.git
            fetch = +refs/heads/*:refs/remotes/origin/*
         */
        addConfig(remoteName, validAddress);
    }

    public void checkIfRemoteNameExisted(String remoteName) {
        /*
        File remoteDir = join(REMOTES_DIR, remoteName);
        if (remoteDir.isDirectory()) {

        }
        *
        */
        String[] contents = readContentsAsString(CONFIG_FILE).split("\n");
        String target = "[remote \"" + remoteName + "\"]";
        for (int i = 0; i < contents.length;) {
            if (contents[i].equals(target)) {
                exit("A remote with that name already exists.");
            }
            i += 2;
        }
    }

    public void rm_remote(String remoteName) {
        checkIfRemoteNameNotExisted(remoteName);
        // TODO:check user info and server valid

        rmConfig(remoteName);
    }

    public void checkIfRemoteNameNotExisted(String remoteName) {
        /*
        File remoteDir = join(REMOTES_DIR, remoteName);
        if (!remoteDir.isDirectory()) {
            exit("A remote with that name does not exist.");
        }
        *
        */
        String[] contents = readContentsAsString(CONFIG_FILE).split("\n");
        String target = "[remote \"" + remoteName + "\"]";
        for (int i = 0; i < contents.length;) {
            if (contents[i].equals(target)) {
                return;
            }
            i += 2;
        }
        exit("A remote with that name does not exist.");
    }

    public void fetch(String remoteName, String remoteBranchName){
        checkIfRemoteNameExisted(remoteName);
        String remoteAddress = readRemoteAddress(remoteName);
        String remoteCWD = new File(remoteAddress).getParent();

        Repository remote = new Repository(remoteCWD);
        remote.checkIfInitialized("Remote directory not found.");
        remote.checkIfBranchNotExisted(remoteBranchName, "That remote does not have that branch.");

        // 如果本地不存在远程branch引用，则创建目录和branch
        // 并将本地记录的remote branch head与remote对应的branch head同步
        File branch = join(REMOTES_DIR, remoteName, remoteBranchName);
        writeContents(branch, remote.readBranchHead(remoteBranchName));

        // 复制本地不包含的所有remote branch 的commit和blob对象到本地objects
        fetchRemoteBranch(remote, remoteBranchName);
    }

    private String readRemoteAddress(String remoteName) {
        String path = "";
        String[] contents = readContentsAsString(CONFIG_FILE).split("\n");
        for (int i = 0; i < contents.length;) {
            if (contents[i].contains(remoteName)) {
                path = contents[i + 1];
                break;
            } else {
                i += 2;
            }
        }
        return path;
    }

    private void fetchRemoteBranch(Repository remote, String remoteBranchName) {
        String remoteBranchHead = remote.readBranchHead(remoteBranchName);
        Commit head = remote.loadCommitByID(remoteBranchHead);
        Queue<Commit> queue = new LinkedList<>();
        queue.add(head);
        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            copyCommitAndBlobs(remote, commit);
            if (!commit.isNoParent()) {
                for (String commitID : commit.getParents()) {
                    queue.add(remote.loadCommitByID(commitID));
                }
            }
        }
    }

    private void copyCommitAndBlobs(Repository remote, Commit remoteCommit){
        File commitFile = join(OBJECTS_DIR, remoteCommit.getID());
        if (commitFile.exists()) {
            return;
        }
        // copy remote commit
        writeObject(commitFile, remoteCommit);

        // now its tracked blobs
        for (Map.Entry<String, String> entry : remoteCommit.getPathToBlobID().entrySet()) {
            String blobId = entry.getValue();
            Blob blob = remote.loadBlobByID(blobId);

            File blobFile = join(OBJECTS_DIR, blobId);
            writeObject(blobFile, blob);
        }
    }

    public void pull(String remoteName, String remoteBranchName) {
        fetch(remoteName, remoteBranchName);

        String otherBranchName = remoteName + File.separator + remoteBranchName;
        merge(otherBranchName);
    }

    public void push(String remoteName, String remoteBranchName) {

    }







}
