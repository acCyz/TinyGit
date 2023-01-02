package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.join;
import static gitlet.Utils.writeObject;

public class Stage implements Serializable{
    /** filepath : blobID */
    private Map<String, String> index;

    public Stage(){
        index = new HashMap<>();
    }

    public void replace(String filePath, String blobID){
        index.replace(filePath, blobID);
    }

    public void add(String filePath, String blobID){
        index.put(filePath, blobID);
    }

    /** remove key:value and return null if not exsited */
    public void delete(String filePath){
        index.remove(filePath);
    }

    public void update(String filePath, String blobID){
        index.put(filePath, blobID);
    }

    public void clear(){
        index.clear();
    }

    public boolean isEmpty(){
        return index.isEmpty();
    }

    public boolean isContainBlob(String filePath, String blobID){
        String queryBlobID = index.getOrDefault(filePath, "");
        return blobID.equals(queryBlobID);
    }

    public void persist(File STAGE_FILE) {
        writeObject(STAGE_FILE, this);
    }



}
