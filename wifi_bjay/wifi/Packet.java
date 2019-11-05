package wifi;
import java.util.zip.CRC32;

public class Packet {
    public static final int NUM_CONTROL_BYTES = 2, DEST_ADDR_BYTES=2, SRC_ADDR_BYTES=2, CRC_BYTES=4;
    public static final int NON_DATA_BYTES = NUM_CONTROL_BYTES+DEST_ADDR_BYTES+SRC_ADDR_BYTES+CRC_BYTES;
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
        int frameLen = NON_DATA_BYTES+len;
        return null;
    }

    /**
     * Fills the control field of the frame
     * @param packet
     * @return
     */
    private byte[] fillControl(byte[] packet){
        return packet;
    }

    /**
     * Fills the address fields of the frame
     * @param packet
     * @return
     */
    private byte[] fillAddresses(byte[] packet){
        return packet;
    }

    /**
     * Fills data field of the frame
     * @param packet
     * @return
     */
    private byte[] fillData(byte[] packet){
        return packet;
    }

    /**
     * Fills the checksum field of the frame
     * @param packet
     * @return
     */
    private byte[] fillCheckSum(byte[] packet){
        return packet;
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

    @Override
    public String toString(){
        StringBuilder build = new StringBuilder();
        build.append("--------------------\nFrame Type: " + frameType + "\n");
        build.append("Retry: " + retry + "\n");
        build.append("Sequence Number: " + seqNum + "\n");
        build.append("Destination: " + dest + "\n");
        build.append("Source: " + src + "\n");
        build.append("Checksum: " + checkSum.getValue() + "\n--------------------");
        return build.toString();
    }



    public static void main(String[] args){
        byte[] data = {1,2,3,4,5,6};
        Packet packet = new Packet(0,0,0,200,100,data,data.length);
        System.out.println(packet);
        System.out.println(packet);
    }

}
