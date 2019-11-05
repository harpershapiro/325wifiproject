package wifi;
import rf.RF;


public class Receiver implements Runnable {
    private int mac;
    private long crc;       //This is the crc that we will save to compare to the packet later
    private byte[] rec_pck; //Place holder for when we rec packet we can store it
    private RF theRF;       //You'll need one of these eventually

    public Receiver(int mac){
        this.mac = mac;

    }

    @Override
    public void run(){
        getData();
    }

    /**
     * Waits for a packet and once it gets one check the CRC, if CRC passed then return the packet and maybe start ack process
     * @return The received packet (after checking it)
     */
    public byte[] getData(){
        rec_pck = theRF.receive(); //This will wait until it receives a packet and only continue once it does
        setCRC(rec_pck); //Set crc before hand (see "base case" of checkCRC()
        if (checkCRC(rec_pck)) {
            System.out.println("getData() CRC check was: True");
            return rec_pck; //check sum was correct go ahead give packet too layer above and send back ack
            //to send the ack well prob call the packet class and rearrange the info as needed
        }
        else {
            System.out.println("getData() CRC check was: False");
            //??? checksum failed do something
            return rec_pck;//bad don't return packet just a place holder for now
        }
    }

    /**
     *
     * @param pck given a packet it will both get the CRC from packet as well as calculate a new one to compare too.
     * @return True if The CRC is equal to our calc CRC
     */
    public boolean checkCRC(byte[] pck) {
        if (crc == -1) { //if passed crc is -1 (aka there all 1's) bypass this step //BASE CASE
            return true;
        }
        //todo: Does the header info count twords the checkSum total or just the data length? //not within scope of CP#2 tho
        //if above is true then //todo: bitwise shift header and crc out from packet and keep data
        // shift 6 (or 48 in bits?) to the left to remove header. then shift 4 (32? bits) to the right to remove CRC
        int pckLength = pck.length; //get the length of data inside of the packet (we - 10 because those are extra header bytes)
        int index = 0;
        long calcCRC = 0;
        while (index < pckLength) { //continue as long as index is less than pckLength (does not handle odd length packets, don't worry we handle that too)
            calcCRC += (((pck[index] << 8) & 0xFF00) | ((pck[index + 1]) & 0xFF));

            index += 2; //get the index two bytes away
        }
        if (index-1 == pckLength) { //If the packet was odd in length then well catch that last byte here else just do final checkSum
            calcCRC += (((pck[index-1] << 8) & 0xFF00));
        }

        return (calcCRC == crc); //return true or false if calcCRC == CRC
    }

    public void setCRC(byte[] pck) {
        //this.crc = crc; //todo: properly get the crc from packet (last 4 bytes) //not within scope of CP#2
    }

}
