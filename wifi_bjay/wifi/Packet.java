package wifi;
import java.util.zip.CRC32;

public class Packet {
    public static final int NUM_CONTROL_BYTES = 2, DEST_ADDR_BYTES=2, SRC_ADDR_BYTES=2, CRC_BYTES=4;
    public static final int HDRBYTES =  NUM_CONTROL_BYTES+DEST_ADDR_BYTES+SRC_ADDR_BYTES;
    public static final int NON_DATA_BYTES = HDRBYTES+CRC_BYTES;
    public static final int FRAME_TYPE=0, RETRY=1, SEQ_NUM=2;
    public static final int MAX_SHORT_VALUE = 65535;


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
        checkSum.update(data,0, len); //todo:UPDATE CHECKSUM WITH HEADERS AND DATA

    }

    public static int extractdest(byte[] b) {
        int dest = 0;
        dest = ((b[2] & 0xff) << 8);   //is the short value of dest from packet
        dest = ((b[3] & 0xff) | dest);
        return dest;
    }

    public static int extractsrc(byte[] b) {
        int src = 0;
        src=  ((b[4] & 0xff) << 8);   //is the short value of dest from packet
        src = ((b[5] & 0xff) | src);
        return src;
    }

    /**
     * @param b byte array aka the packet
     * @param cmd CMD is the whitch value we witch to grab in the control bytes, 0 is frame type, 1 is retry bit, 2 is seq number
     * @return one of the cmd types explained above
     */
    public static int extractcontrl(byte[] b, int cmd) {
        int value = 0;

        //int seq = 0;
        //set value to be the 16 control bits (with 16 left-padded 0s since it's an int)
        value =  ((b[0] & 0xff) << 8);
        value =  ((b[1] & 0xff) | value);
        System.out.println("Extract control string "+ Integer.toBinaryString(value));
        if (cmd == FRAME_TYPE) {
            value = value >>> 13;
        }
        else if (cmd == RETRY) {
            System.out.println("Extracting retry, starting with " + Integer.toBinaryString(value));
            value = value << 19;
            value = value >>> 31;
        }
        else if (cmd == SEQ_NUM) {
            System.out.println("Extracting seqNum, starting with " + Integer.toBinaryString(value));
            value = value << 20;
            System.out.println("Next " + Integer.toBinaryString(value));
            value = value >>> 20;
            System.out.println("Finally " + Integer.toBinaryString(value));

        } //sorry brad :^ ] (we had to write this)
        return value;
    }


    /**
     * Set frame type.
     * @param frameType
     */
    public void setFrameType(int frameType){
        this.frameType = frameType;
    }
    public void setRetry(int retry){
        this.retry = retry;
    }
    ////////////ADD ALL SETTERS AND GETTERS?///////////////////////////////////////////////

    /**
     * Creates a transmittable byte array from the packet object.
     * @return the byte array version of this packet
     */
    public byte[] getFrame(){
        //create new frame array of proper size
        int frameLen = NON_DATA_BYTES+len;
        byte[] frame = new byte[frameLen];

        //fill in the fields
        frame = fillControl(frame);
        frame = fillAddresses(frame);
        frame = fillData(frame);
        frame = fillCheckSum(frame);

        return frame;
    }

    public int getDest(){
        return dest;
    }

    /**
     * Fills the control field of the frame
     * @param frame frame with missing control-bits field
     * @return filled frame
     */
    private byte[] fillControl(byte[] frame){
        //do some shifting and combining to get full control sequence (max 2 bytes)
        int control = (((frameType<<1)|retry)<<12)|seqNum;
        //fill in frame
        frame[0] = (byte)(control>>8);
        frame[1] = (byte)(control);
        return frame;
    }

    /**
     * Fills the address fields of the frame
     * @param frame
     * @return
     */
    private byte[] fillAddresses(byte[] frame){
        //shift both addresses to place into bytes 3-6 in frame
        frame[2] = (byte)((dest>>8));
        frame[3] = (byte)dest;
        frame[4] = (byte)((src>>8));
        frame[5] = (byte)src;
        return frame;
    }

    /**
     * Fills data field of the frame
     * @param frame
     * @return
     */
    private byte[] fillData(byte[] frame){
        int frameLen = NON_DATA_BYTES+len;
        for(int i = HDRBYTES; i < frameLen-CRC_BYTES;i++) {
            frame[i] = data[i-HDRBYTES];
        }
        return frame;
    }

    /**
     * Fills the checksum field of the frame
     * @param frame
     * @return
     */
    private byte[] fillCheckSum(byte[] frame){
        int frameLen = NON_DATA_BYTES+len;
        //cast long crc to int to make it bytes
        long crc = getCheckSum();
        frame[frameLen-4] = (byte)((crc>>24));   //shift by 24 so we we only grab the 8 left most bits
        frame[frameLen-3] = (byte)(crc>>16);
        frame[frameLen-2] = (byte)((crc>>8));
        frame[frameLen-1] = (byte)((crc));       //don't shift so we we only grab the 8 right most bits

        return frame;
    }

    private long getCheckSum(){
        // all 1's in binary to bypass checksum implementation for testing purposes
        return -1;//checkSum.getValue();
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
        Packet packet = new Packet(1,0,255,255,255,data,data.length);
        System.out.println(packet);
        byte[] frame = packet.getFrame();
        System.out.println(Integer.toBinaryString(frame[0]));
        System.out.println(Integer.toBinaryString(frame[1]));
        System.out.println("Dest : "+ extractdest(frame));
        System.out.println("Src  : "+ extractsrc(frame));
        System.out.println("Seq  : "+ extractcontrl(frame,SEQ_NUM));
        System.out.println("retry  : "+ extractcontrl(frame,RETRY));
        System.out.println("frameType  : "+ extractcontrl(frame,FRAME_TYPE));



//        System.out.println("Byte 3: " + Integer.toBinaryString(frame[2]));
//        System.out.println("Byte 4: " + Integer.toBinaryString(frame[3]));
    }

}