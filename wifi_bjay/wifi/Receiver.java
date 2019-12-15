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
    private RF theRF;       //You'll need one of these eventually
    private CRC32 checksum;
    private ArrayBlockingQueue<Transmission> dataIncoming;
    private AtomicInteger ackFlag; //value of the sequence number of an ack received in receiver thread (shared)
    private PrintWriter output;
    private HashMap<Short,Integer> srcToSequence; //maps src address to next sequence number
    private long ourTime; //This is what this thread thinks is the current time


    public Receiver(int mac, rf.RF theRF, ArrayBlockingQueue<Transmission> dataIncoming, AtomicInteger ackFlag, PrintWriter output,long ourTime){
        this.mac = mac;
        this.theRF = theRF;
        this.dataIncoming = dataIncoming;
        this.output = output;
        this.ackFlag = ackFlag;
        this.srcToSequence = new HashMap<Short,Integer>();
        this.ourTime = ourTime;
        this.checksum = new CRC32();
    }

    @Override
    public void run(){
        byte[] rec_frame; //variable to store our receipt from RF layer
        byte[] data;
        Wait waiting = new Wait(theRF,theRF.aCWmin,100);
        boolean duplicateData;

        while(true) {
            duplicateData = false; //we want to make sure  not to deliver duplicates
            if(LinkLayer.debug==1) output.println("Waiting for packets");
            //check if there is some data to receive, sleep for a bit otherwise
            while(!theRF.dataWaiting()){
                try {
                    sleep(50);
                } catch (InterruptedException e){
                    continue; //just go back to top if this didn't work
                }
            }
            rec_frame = theRF.receive(); //will wait until a data comes in
            int frameType = Packet.extractcontrl(rec_frame,Packet.FRAME_TYPE);

            //If we ran out of buffer for data, ignore
            if(frameType==0 && dataIncoming.size()>=LinkLayer.QUEUE_CAPACITY){
                continue;
            }
            //Start CRC checking of recv packet
            int extractCRC = Packet.extractCRC(rec_frame);
            byte[] crcBytes = Arrays.copyOfRange(rec_frame,0,rec_frame.length-Packet.CRC_BYTES);
            checksum.reset();
            checksum.update(crcBytes);
            long calcCRC = checksum.getValue();
            int calcCRCint = (int) checksum.getValue();
//            System.out.println("GET calcCRC     :  " +calcCRC);
//            System.out.println("GET calcCRCint  :  " +calcCRCint);
            if (extractCRC != calcCRCint) {
                //todo: The CRC's don't match! tell sender
                if(LinkLayer.debug>=1) output.println("CRC's don't match extractCRC :"+ extractCRC + "\ncalcCRC :"+calcCRC);
            }
            else {
                if(LinkLayer.debug>=1) output.println("CRC's MATCHED!!! :"+ calcCRC);
            }
            //End CRC check

            //If it's an ACK, need to let the sender know
            if(frameType ==1) {
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

                //If it's an Beacon frame then do beacon related things
                if (frameType == 2 ) {
                    if(LinkLayer.debug>=1) output.println("Received a Beacon");
                    //Grab data to get "thereTime" value to compare to our own
                    long thereTime = 0;
                    for (int i = 0; i < data.length; i++) //in theory this loop should only happen 8 times (long = 8 bytes)
                    {
                        thereTime = (thereTime << 8) + (data[i] & 0xff); //converting the byte array to a long value
                    }
                    ourTime = LinkLayer.getClock();
//                    if(LinkLayer.debug>=1) output.println("ourTime is      "+ ourTime);
//                    if(LinkLayer.debug>=1) output.println("ThereTime was "+ thereTime);
//                    if(LinkLayer.debug>=1) output.println("Difference : "+ (thereTime - ourTime));
//                    if(LinkLayer.debug>=1 && ourTime > thereTime) output.println("Offset : "+ LinkLayer.getClockOffset()); //todo: remove before final turn in
                    if (ourTime < thereTime) {
//                        if(LinkLayer.debug>=1) output.println("Offset before : "+ LinkLayer.getClockOffset());
                        LinkLayer.setClockOffset(LinkLayer.getClockOffset() + (thereTime - ourTime));
//                        if(LinkLayer.debug>=1) output.println("Offset : "+ LinkLayer.getClockOffset());
                        if(LinkLayer.debug>=1) output.println("Accepting there time :"+ thereTime);
                    }
                    else if(LinkLayer.debug>=1) output.println("Not accepting there time");
                }
                //End Beacon related things

                //Check for any gaps and update sequence number map
                if(!srcToSequence.containsKey(src)){
                    srcToSequence.put(src,0); //we want sequence #0 next
                }
                int seqNum = Packet.extractcontrl(rec_frame, Packet.SEQ_NUM);
                int expectedSeqNum = srcToSequence.get(src);//ACK packet will hold the seqNum we received
                if(expectedSeqNum<seqNum && dest!=-1) output.println("WARNING: Packet may have been lost.");
                else if(expectedSeqNum>seqNum && dest!=-1){
                    if(LinkLayer.debug==1) output.println("Duplicate data received");
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
                        waiting.SIFS();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    theRF.transmit(ack.getFrame());
                }

                //DELIVER DATA if it's good data
                if(!duplicateData && frameType==0) dataIncoming.add(rec_trans);
            }
        }
    }
}
