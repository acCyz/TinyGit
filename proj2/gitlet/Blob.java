package gitlet;

import java.io.File;
import java.io.Serializable;

import static gitlet.Utils.*;

public class Blob implements Serializable{
    private final String id;
    private final byte[] content;

    /** TODO: blob里不存放文件名和路径, 包括计算hash时也不用文件名和路径 */
    //private String filePath;
    public Blob(File file){
        //this.filePath = file.getName();
        this.content = generateContent(file);
        this.id = generateSha1ID();
    }

    private byte[] generateContent(File file) {
        return readContents(file);
    }
    private String generateSha1ID(){
        return sha1((Object) this.content);
    }

    public String getID(){
        return this.id;
    }
    public byte[] getContent(){
        return this.content;
    }

    /*
    public String getFilePath() {
        return this.filePath;
    }
    */

    public void persist(File BLOB_DIR){
        File file = join(BLOB_DIR, this.getID()); // now, without Tries firstly...
        writeObject(file, this);
    }
}
