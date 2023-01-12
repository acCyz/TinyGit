package gitlet;

import java.io.File;
import java.io.Serializable;

import static gitlet.Utils.*;

public class Blob implements Serializable {
    private final String id;
    private final byte[] content;

    public Blob(File file) {
        //this.filePath = file.getName();
        this.content = generateContent(file);
        this.id = generateSha1ID();
    }

    public Blob(byte[] content) {
        //this.filePath = file.getName();
        this.content = content;
        this.id = generateSha1ID();
    }

    private byte[] generateContent(File file) {
        return readContents(file);
    }
    private String generateSha1ID() {
        return sha1((Object) this.content);
    }

    public String getID() {
        return this.id;
    }
    public byte[] getContent() {
        return this.content;
    }

    public void persist(File blobDir) {
        File file = join(blobDir, this.getID());
        writeObject(file, this);
    }
}
