package wifi;
import java.util.zip.CRC32;

public class Packet {
    private int frameType;
    private int retry;
    private int seqNum;
    private int dest;
    private int src;
    private byte[] data;
    private int len;
    private CRC32 checkSum;

    public Packet(int frameType, int retry, int seqNum, int dest, int src, byte[] data, int len){
        this.frameType = frameType;
        this.retry = retry;
        this.seqNum = seqNum;
        this.dest = dest;
        this.src = src;
        this.data = data;
        this.len = len;

        this.checkSum = new CRC32();
        checkSum.update(data,0, len);

    }

    /**
     * Set frame type.
     * @param frameType
     */
    public void setFrameType(int frameType){
        this.frameType = frameType;
    }
    ////////////ADD ALL SETTERS AND GETTERS!///////////////////////////////////////////////

    /**
     * Creates a transmittable frame from the packet object.
     * @return
     */
    public byte[] getFrame(){
        return null;
    }

    private long getCheckSum(){
        return -1; // all 1's in binary to bypass checksum implementation for testing purposes
        //return checkSum.getValue();
    }

    /**
     * Creates a 2-byte array from the control bits (frametype, retry, seqNum)
     * @return
     */
    private byte[] createControlBytes(){
        return null;
    }









}
