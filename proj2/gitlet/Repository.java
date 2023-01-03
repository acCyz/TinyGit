package gitlet;


import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static gitlet.Utils.*;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     *
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

    public static void print(){
        System.out.print(CWD);
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

    private static void setOrCreateBranch(String branchName, String commitID){
        File HEADS_FILE = join(HEADS_DIR, branchName);
        writeContents(HEADS_FILE, commitID);
    }

    private static void deleteBranch(String branchName) {
        File removeBranch = join(HEADS_DIR, branchName);
        restrictedDelete(removeBranch);
    }

    private static void initHEAD(){
        writeContents(HEAD_FILE, DEFAULT_BRANCH_NAME);
    }

    /** command add[filename]
     * TODO: support multi files
     * */
    public static void add(String filePath) {
        File file = getFileFromCWD(filePath);
        if (!file.exists()) {
            exit("File does not exist.");
        }
        // 创建blob对象
        Blob blob = new Blob(file);
        // 将blob对象持久化，并检查更新暂存区
        addBlob(file, blob);
    }

    private static File getFileFromCWD(String filePath) {
        return Paths.get(filePath).isAbsolute()   // path may out of CWD
                ? new File(filePath)
                : join(CWD, filePath);
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
            /*
            if(addStage.isContainBlob(filePath, blobID)) {
                addStage.delete(filePath);
            }
            if(removeStage.isContainBlob(filePath, blobID)) {
                removeStage.delete(filePath);
            }*/
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

    public static Stage loadAddStage(){
        if (!ADDSTAGE_FILE.exists()) {
            return new Stage();
        }
        return readObject(ADDSTAGE_FILE, Stage.class);
    }

    public static Stage loadRemoveStage(){
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
            File CURR_COMMIT_FILE = join(OBJECTS_DIR, commitID);
            if (!CURR_COMMIT_FILE.exists()) {
                return null;
            }
            return readObject(CURR_COMMIT_FILE, Commit.class);
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
    private static String readCurCommitID() {
        String currBranch = readCurBranch();
        File HEADS_FILE = join(HEADS_DIR, currBranch);
        return readContentsAsString(HEADS_FILE);
    }

    private static String readCurBranch() {
        return readContentsAsString(HEAD_FILE);
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

        // 更新branch和head
        updateHeads(newCommit);

        // 清空并持久化暂存区
        clearStage(addStage, removeStage);
        persistStage(addStage, removeStage);
    }

    public static boolean checkIfStageChanged(Stage addStage, Stage removeStage){
        return !addStage.isEmpty() || !removeStage.isEmpty();
    }

    public static Commit generateNewCommit(String message, Commit preCommit, Stage addStage, Stage removeStage){
        Map<String, String> newPathToBlob = generateNewPathToBlob(addStage, removeStage, preCommit.getPathToBlobID());
        List<String> newParents = generateNewParents(preCommit);
        return new Commit(message, newPathToBlob, newParents);
    }

    public static Map<String, String> generateNewPathToBlob(Stage addStage, Stage removeStage, Map<String, String> pathToBlob){
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

    public static List<String> generateNewParents(Commit preCommit){
        List<String> newParents = new ArrayList<>();
        newParents.add(preCommit.getID());
        return newParents;
    }

    public static void updateHeads(Commit newCommit){
        String curBranchName = getCurBranchName();
        File HEADS_FILE = join(HEADS_DIR, curBranchName);
        writeContents(HEADS_FILE, newCommit.getID());
    }

    public static String getCurBranchName(){
        return readContentsAsString(HEAD_FILE);
    }

    public static void clearStage(Stage addStage, Stage removeStage){
        addStage.clear();
        removeStage.clear();
    }

    public static void persistStage(Stage addStage, Stage removeStage){
        addStage.persist(ADDSTAGE_FILE);
        removeStage.persist(REMOVESTAGE_FILE);
    }

    public static void rm(String filePath) {
        File file = getFileFromCWD(filePath);

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

    public static String getBlobIDFromCurCommit(Commit curCommit, String filePath){
        return curCommit.getBlobIDOf(filePath);
    }


    public static void log(){

    }

    public static void branch(String branchName){
        checkIfNewBranch(branchName);
        Commit curCommit = loadCurCommit();
        setOrCreateBranch(branchName, curCommit.getID());
    }

    public static void rm_branch(String branchName){
        checkIfNewBranch(branchName);
        checkIfIsCurBranch(branchName);
        deleteBranch(branchName);
    }

    private static boolean isBranchExisted(String branchName){
        List<String> allBranches = plainFilenamesIn(HEADS_DIR);
        return allBranches != null && allBranches.contains(branchName);
    }
    private static void checkIfNewBranch(String branchName) {
        if (isBranchExisted(branchName)) {
            exit("A branch with that name already exists.");
        }
    }
    private static void checkIfIsCurBranch(String branchName){
        if(branchName.equals(getCurBranchName())){
            exit("Cannot remove the current branch.");
        }
    }

    public static void reset(String commitID){
        Commit dstCommit = loadCommitByID(commitID);
        if(dstCommit == null){
            exit("No commit with that id exists.");
        }
        checkIfCurBranchHasUntrackedFiles();

        deleteDstCommitUntrackedFiles();

        clearStageAndPersist();

        resetBranchHeadTo(commitID);
    }

    public static void checkIfCurBranchHasUntrackedFiles() {
        // TODO:增加对多层目录的文件检测
        Commit curCommit = loadCurCommit();
        List<String> CWDFiles = plainFilenamesIn(CWD);
    }

    private static void clearStageAndPersist(){
        Stage addStage = loadAddStage();
        addStage.clear();
        addStage.persist(ADDSTAGE_FILE);
        Stage removeStage = loadRemoveStage();
        removeStage.clear();
        removeStage.persist(REMOVESTAGE_FILE);
    }
    
    public static void deleteDstCommitUntrackedFiles(){

    }

    public static void resetBranchHeadTo(String commitID){
        String branchName = getCurBranchName();
        setOrCreateBranch(branchName, commitID);
    }



    public static void checkIfInitialized() {
        if (!GITLET_DIR.isDirectory()) {
            exit("Not in an initialized Gitlet directory.");
        }
    }

}
