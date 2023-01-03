package gitlet;

// TODO: any imports you need here

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.Date; // TODO: You'll likely use this in this class
import static gitlet.Utils.*;

/** Represents a gitlet commit object.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Commit implements Serializable {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** 注意，所有static标签标记的变量都不会被存储，所以声明变量时不要加static */
    /** The message of this Commit. */
    private String id;

    private String message;

    //private Date currentTime;

    //private File commitSaveFileName;

    private String timeStamp;

    private Map<String, String> pathToBlobID;

    private List<String> parents;

    /** default constructor */
    public Commit(){
        this.message = "initial commit";
        this.timeStamp = dateToTimeStamp(new Date());
        this.parents = new ArrayList<>();
        this.pathToBlobID = new HashMap<>();
        this.id = generateSha1ID();
    }

    public Commit(String message, Map<String, String> pathToBlobID, List<String> parents) {
        this.message = message;
        this.timeStamp = dateToTimeStamp(new Date());
        this.parents = parents;
        this.pathToBlobID = pathToBlobID;
        this.id = generateSha1ID();
    }

    private String generateSha1ID(){
        return sha1(this.message,this.timeStamp,this.parents.toString(),this.pathToBlobID.toString());
    }

    public String getID() {
        return this.id;
    }
    public Map<String,String> getPathToBlobID(){
        return pathToBlobID;
    }
    public String getBlobIDOf(String filePath){
        return pathToBlobID.getOrDefault(filePath, "");
    }
    public List<String> getParents(){
        return parents;
    }

    /** 检查该commit跟踪的filepath文件对应的版本，是否和传入的版本相同 */
    public boolean isTrackedSameBlob(String filePath, String blobID){
        String queryBlobID = pathToBlobID.getOrDefault(filePath, "");
        return blobID.equals(queryBlobID);
    }

    /** 检查该commit是否跟踪了该文件*/
    public boolean isTrackedFile(String filePath){
        return pathToBlobID.containsKey(filePath);
    }

    public void persist(File COMMIT_DIR) {
        File file = join(COMMIT_DIR, this.getID()); // now, without Tries firstly...
        writeObject(file, this);
    }




}
