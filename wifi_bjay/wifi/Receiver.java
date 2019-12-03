package wifi;
import rf.RF;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
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
    private HashMap<Short,Integer> srcToSequence; //maps src address to next sequence number


    public Receiver(int mac, rf.RF theRF, ArrayBlockingQueue<Transmission> dataIncoming, AtomicInteger ackFlag, PrintWriter output){
        this.mac = mac;
        this.theRF = theRF;
        this.dataIncoming = dataIncoming;
        this.output = output;
        this.ackFlag = ackFlag;
        this.srcToSequence = new HashMap<Short,Integer>();
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
        Wait waiting = new Wait(theRF,theRF.aCWmin,100);
        boolean duplicateData;

        while(true) {
            //check if there is some data to receive, sleep for a bit otherwise
            duplicateData = false; //we want to make sure  not to deliver duplicates
            if(LinkLayer.debug==1) output.println("RECV Waiting for packets");
            while(!theRF.dataWaiting()){
                try {
                    sleep(50); //todo: look at datamations and find out how long to wait (add to waiting object)
                } catch (InterruptedException e){
                    continue; //just go back to top if this didn't work
                }
            }
            rec_frame = theRF.receive(); //will wait until a data comes in

            //If it's an ACK, need to let the sender know
            if(Packet.extractcontrl(rec_frame,Packet.FRAME_TYPE) ==1) {
                int seqNum = Packet.extractcontrl(rec_frame,Packet.SEQ_NUM);
                if(LinkLayer.debug==1) output.println("Receiver got a possible Ack, Sequence number was "+ seqNum);
                int dest = Packet.extractdest(rec_frame);
                if(dest==mac){
                    //send news with sequence number to sender thread to compare
                    ackFlag.set(seqNum);
                }
                continue;
            }

            //Package data for delivery
            data = Arrays.copyOfRange(rec_frame,6, (rec_frame.length - Packet.CRC_BYTES)); //grab data from index 6 to len-4
            short dest = (short)Packet.extractdest(rec_frame);
            output.println("Receiver got a data frame sent for " + dest);
            short src = (short)Packet.extractsrc(rec_frame);



            //CHECK if Data is actually for us (either broadcast address or our personal MAC)
            if (dest == (short)mac || dest == -1) {

                //Check for any gaps and update sequence number map
                if(!srcToSequence.containsKey(src)){
                    srcToSequence.put(src,0); //we want sequence #0 next
                }
                int seqNum = Packet.extractcontrl(rec_frame, Packet.SEQ_NUM);
                int expectedSeqNum = srcToSequence.get(src);//ACK packet will hold the seqNum we received
                if(expectedSeqNum<seqNum && dest!=-1) output.println("WARNING: Packet may have been lost.");
                else if(expectedSeqNum>seqNum){
                    if(LinkLayer.debug==1) output.println("Duplicate data receieved");
                    duplicateData = true;
                }
                srcToSequence.replace(src,seqNum+1);

                Transmission rec_trans = new Transmission((short)dest,(short)src,data);


                //SEND AN ACK
                if(dest!=-1) {
                    Packet ack = new Packet(1, 0, seqNum, src, dest, new byte[0], 0);
                    if(LinkLayer.debug==1)output.println("Sending an ACK to " + src + " for sequence #"+seqNum);
                    //WAIT SIFS
                    try {
                        //sleep(RF.aSIFSTime); //todo: create waiting object and call sifs
                        waiting.SIFS();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    theRF.transmit(ack.getFrame());
                }

                //DELIVER DATA
                if(!duplicateData) dataIncoming.add(rec_trans);
            }
        }
    }
}
