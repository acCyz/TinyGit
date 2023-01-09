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
        this.pathToBlobID = new TreeMap<>();
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
    public String getMessage(){return this.message;}
    public Map<String,String> getPathToBlobID(){
        return pathToBlobID;
    }
    public String getBlobIDOf(String filePath){
        return pathToBlobID.getOrDefault(filePath, "");
    }
    public List<String> getParents(){
        return parents;
    }

    /** 检查给定的文件，是否会被当前commit中的同名文件覆盖 */
    public boolean willOverwrite(String filePath, String blobID){
        return isTrackedFile(filePath) && !isTrackedSameBlob(filePath, blobID);
    }

    /** 检查该commit跟踪的了相同的文件版本 */
    public boolean isTrackedSameBlob(String filePath, String blobID){
        String queryBlobID = pathToBlobID.get(filePath);
        return isTrackedFile(filePath) && blobID.equals(queryBlobID);
    }

    /** 检查该commit是否跟踪了该文件*/
    public boolean isTrackedFile(String filePath){
        return pathToBlobID.containsKey(filePath);
    }

    public boolean isNoParent(){
        return this.parents.isEmpty();
    }

    public boolean equals(Commit other){
        return this.id.equals(other.getID());
    }

    public List<String> getInfo(){
        ArrayList<String> info = new ArrayList<>();
        //info.add("===");  // had been wrapped in outside
        info.add("commit " + this.id);
        if(parents.size() > 1){
            StringBuilder pString = new StringBuilder("");
            for(String p : parents){
                pString.append(p, 0, 7).append(" ");
            }
            info.add("Merge: " + pString);
        }
        info.add("Date: " + this.timeStamp);
        info.add(this.message);
        //info.add(" ");
        return info;
    }


    public void persist(File COMMIT_DIR) {
        // TODO:真正的git是将id的前2位作为子目录名，后38位作为2位目录下的文件名
        File file = join(COMMIT_DIR, this.getID()); // now, without Tries firstly...
        writeObject(file, this);
    }





}
