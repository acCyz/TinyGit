package gitlet;


import java.io.File;
import java.nio.file.Paths;

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

        File HEADS_FILE = join(HEADS_DIR, DEFAULT_BRANCH_NAME);
        writeContents(HEADS_FILE, initCommit.getID());
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
        if(curCommit.isContainBlob(filePath, blobID)){
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
        // 保存commit对象
        Commit newCommit = generateNewCommit(preCommit, addStage, removeStage);

        newCommit.persist(OBJECTS_DIR);

        // 更新brach和head

        clearStage(addStage, removeStage);
        persistStage(addStage, removeStage);
    }

    public static boolean checkIfStageChanged(Stage addStage, Stage removeStage){
        return !addStage.isEmpty() || !removeStage.isEmpty();
    }

    public static Commit generateNewCommit(Commit preCommit, Stage addStage, Stage removeStage){
        Commit newCommit = new Commit();
        return newCommit;
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

    }


    public static void checkIfInitialized() {
        if (!GITLET_DIR.isDirectory()) {
            exit("Not in an initialized Gitlet directory.");
        }
    }

}
