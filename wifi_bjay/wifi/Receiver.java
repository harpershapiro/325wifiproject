package wifi;
import rf.RF;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

import static java.lang.Thread.sleep;



public class Receiver implements Runnable {
    private int mac;
    private long crc;       //This is the crc that we will save to compare to the packet later
    //private byte[] rec_pck; //Place holder for when we rec packet we can store it
    private RF theRF;       //You'll need one of these eventually
    private CRC32 checksum;
    private ArrayBlockingQueue<Transmission> dataIncoming;
    private AtomicInteger ackFlag; //value of the sequence number of an ack received in receiver thread (shared)
    private PrintWriter output;


    public Receiver(int mac, rf.RF theRF, ArrayBlockingQueue<Transmission> dataIncoming, AtomicInteger ackFlag, PrintWriter output){
        this.mac = mac;
        this.theRF = theRF;
        this.dataIncoming = dataIncoming;
        this.output = output;
        this.ackFlag = ackFlag;

    }


    /**
     * Waits for a packet and once it gets one check the CRC, if CRC passed then return the packet and maybe start ack process
     * @return The received packet (after checking it)
     */
    /*
    public byte[] getData(){
        rec_pck = theRF.receive();  //This will wait until it receives a packet and only continue once it does
        setCRC(rec_pck);            //Set crc before hand (see "base case" of checkCRC()
        if (checkCRC(rec_pck)) {
            System.out.println("getData() CRC check was: True");
            return rec_pck; //check sum was correct go ahead give packet too layer above and send back ack
                            //to send the ack well prob call the packet class and rearrange the info as needed
        }
        else {
            System.out.println("getData() CRC check was: False");
            //??? checksum failed do something else
            return rec_pck; //bad don't return packet just a place holder for now
        }
    }*/

    /**
     *
     * @param pck given a packet it will both get the CRC from packet as well as calculate a new one to compare too.
     * @return True if The CRC is equal to our calc CRC
     */
    public boolean checkCRC(byte[] pck) { //todo: replace this method with one that uses CRC32 after we grab the data from packet then compare it
        if (crc == -1) { //if passed crc is -1 (aka there all 1's) bypass this step //BASE CASE
            return true;
        }
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

    @Override
    public void run(){
        byte[] rec_frame; //variable to store our receipt from RF layer
        byte[] data;

        while(true) {
            //check if there is some data to receive, sleep for a bit otherwise
            output.println("RECV Waiting for packets");
            while(!theRF.dataWaiting()){
                try {
                    sleep(50); //not sure how long to sleep yet
                } catch (InterruptedException e){
                    continue; //just go back to top if this didn't work
                }
            }
            rec_frame = theRF.receive(); //will wait until a data comes in

            //If it's an ACK, need to let the sender know
            if(Packet.extractcontrl(rec_frame,Packet.FRAME_TYPE) ==1) {
                output.println("Received a possible ACK");
                int seqNum = Packet.extractcontrl(rec_frame,Packet.SEQ_NUM);
                int dest = Packet.extractdest(rec_frame);
                if(dest==mac){
                    //send news with sequence number to sender thread to compare
                    ackFlag.set(seqNum);
                }
                continue;
            }

            //Package data for delivery
            data = Arrays.copyOfRange(rec_frame,6, (rec_frame.length - Packet.CRC_BYTES)); //grab data from index 6 to len-4
            int dest = Packet.extractdest(rec_frame);
            int src = Packet.extractsrc(rec_frame);
            Transmission rec_trans = new Transmission((short)dest,(short)src,data); //todo: change (short)-1's to there proper values (done)

            //CHECK if Data is actually for us
            if (dest == mac || dest == -1) {

                //short control = (short)(((rec_frame[0] & 0xFF) << 8) | (rec_frame[1] & 0xFF)); // DO WE NEED THIS?
                //System.out.println("RECV rec_pck: "+ rec_frame);

                //SEND AN ACK
                if(dest!=-1) {
                    int seqNum = Packet.extractcontrl(rec_frame, Packet.SEQ_NUM); //ACK packet will hold the seqNum we received
                    Packet ack = new Packet(1, 0, seqNum, src, dest, new byte[0], 0);
                    output.println("Sending an ACK for "+seqNum);
                    //WAIT SIFS
                    try {
                        sleep(RF.aSIFSTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    theRF.transmit(ack.getFrame());
                }
                //Deliver data
                dataIncoming.add(rec_trans);
            }
        }
//            try {
//
//                sleep(1000);
//            } catch (InterruptedException e) {
//                System.out.println("Interrupted.");
//            }
        //grab data from rec_pck create a new transmission object, fill that objet with the data and place holder for the hdr
        //Add to the incommingdata Queue

    }
}
