package wifi;
import rf.RF;

import java.io.PrintWriter;
import java.nio.Buffer;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;


public class Sender<Final> implements Runnable {
    private final int WAITING_4_DATA = 0, WAITING_4_MED_ACCESS = 1, WAITING_DIFS = 2, WAITING_BACKOFF = 3, WAITING_4_ACK = 4;
    private int mac;
    private boolean retry; //true if we are resending current packet
    private boolean fullyIdle; //true if we have never seen the channel busy during this transmission
    private int state;
    private  ArrayBlockingQueue<Transmission> dataOutgoing;
    private rf.RF theRF;
    private PrintWriter output;
    private Packet datapck;
    private int retryAttempts;
    private int seqNum;
    private short dest;
    private HashMap<Short,Integer> destToSequence; //maps dest to sequence number
    private AtomicInteger ackFlag; //value of the sequence number of an ack received in receiver thread (shared)

    private boolean sendBeacon; //every 100ms or so we make this true and when the current packet ends we send beacon frame before grabbing the next data.
    private long ourTime;
    private long startT;
    private long endT; //if endT is more than 100 larger than startT set sendBeacon to true
    final private int FUDGEFACTOR = 2402; //beacon transmission estimation //1802 (prev estimated time)


    public Sender(int mac, ArrayBlockingQueue<Transmission> dataOutgoing, rf.RF theRF,AtomicInteger ackFlag, PrintWriter output){
        this.mac = mac;
        this.retry = false;
        this.fullyIdle = false;
        this.state = WAITING_4_DATA; //when thread is called start at waiting4data to start
        this.dataOutgoing = dataOutgoing;
        this.theRF = theRF;
        this.output = output;
        this.datapck=null;
        this.retryAttempts=0;
        this.destToSequence = new HashMap<Short,Integer>();
        this.ackFlag = ackFlag;
        this.sendBeacon=false;
        this.startT=0;
        this.endT=0;
    }

    public void resetTransmission(){
        fullyIdle = false;
        datapck = null;
        retry = false;
        retryAttempts = 0;
    }

    @Override
    public void run(){

        //STATE MACHINE FOR 802.11~ SENDER SPEC

        Wait waiting = new Wait(theRF,theRF.aCWmin,50); //50 50ms slots for ack timeout
        boolean setBackoff = true; //determines whether or not we are in the middle of an expontential backoff countdown

        while(true) {
            if (setBackoff) {
                waiting.setRanBackoff(); //each loop we set the Window backoff size to use this loop (we reloop when this changes anyway so its safe)
            }
            switch (state) {

                case WAITING_4_DATA://///////////////////////////////////////////////////////////////////////////////

                   //BRAND NEW TRANSMISSION
                    startT = LinkLayer.getClock(); //get the clock as we start a new loop
                    if(sendBeacon) {
                        if(LinkLayer.debug >= 1) output.println("\tCreating A Beacon Frame!!");
                        ourTime = LinkLayer.getClock() + FUDGEFACTOR;
                        byte[] data = new byte[8];
                        for (int i = 7; i >= 0; i--) {
                            data[i] = (byte)(this.ourTime & 0xFF);
                            ourTime >>= 8;
                        }
                        this.datapck = new Packet(2, 0, 0, -1, mac , data, data.length);
                    }

                    //End setting up beacon packet proceed with sender logic
                   else if(datapck==null) {
                       Transmission data; //holds what we grabbed from queue
                       //Carefully look for data to send + escape if beacon transmission needed
                        while(true) {
                            try {
                                data = dataOutgoing.peek();
                                //Check for beacon
                                endT = LinkLayer.getClock(); //set the endT to clock and compare to startT
                                if (startT + LinkLayer.getBeaconBackoff() < endT && LinkLayer.beaconsEnabled) {
                                    sendBeacon = true;
                                    break; //ignore any data we saw and go to Beacon case
                                } else sendBeacon = false;

                                //Take data
                                if (data != null) {
                                    data = dataOutgoing.take();
                                    break;
                                } else {
                                    continue;
                                }
                            } catch (InterruptedException e) {
                                continue;
                            }
                        }
                        if(sendBeacon) break; //SORRY but we need to go back to the top.

                       //Get some addresses and upkeep sequence number hashmap
                       //short src = data.getSourceAddr();
                       this.dest = data.getDestAddr();
                       if(!destToSequence.containsKey(dest)){
                           destToSequence.put(dest,0);
                       }
                       this.seqNum = destToSequence.get(dest);

                       //make our packet to transmit
                       this.datapck = new Packet(000, 0, seqNum, dest, mac , data.getBuf(), data.getBuf().length);

                   //RETRY TRANMISSION (since we still have leftover data to send)
                   } else {
                       datapck.setRetry(1);
                   }

                    //assign a new state depending on medium access
                    if (theRF.inUse()) {
                        state = WAITING_4_MED_ACCESS;
                        break;
                    }
                    else {
                        if (!retry) fullyIdle = true; //only fully idle if new transmission
                        state = WAITING_DIFS;
                        break;
                    }

                case WAITING_4_MED_ACCESS:///////////////////////////////////////////////////////////////////////////
                    //check if RF in use, If false then line not in use wait DIFS
                    if (theRF.inUse()) {
                        try {
                            sleep(5); //sleep a little to save cpu
                        } catch (InterruptedException e) {
                            continue;
                        }
                        break;
                    }
                    else { //channel is free
                        state = WAITING_DIFS;
                        break;
                    }

                case WAITING_DIFS: /////////////////////////////////////////////////////////////////////////////////
                    try {
                        if(LinkLayer.debug ==1) output.println("Waiting DIFS...");
                        waiting.DIFS();
                    } catch (InterruptedException e) {
                        continue;
                    }

                    //time elapsed & medium is not idle
                    if (theRF.inUse()) {
                        if(LinkLayer.debug ==1) output.println("Channel was in use after DIFS.");
                        state = WAITING_4_MED_ACCESS;
                        break;
                    }

                    //time elapsed & medium is idle & we've already waited for medium access
                    if (!fullyIdle && !theRF.inUse()) { //good. line not in use. start backoff.
                        state = WAITING_BACKOFF;                //If fullyIdle is false then must be right branch of MAC rules
                        break;
                    }
                    //time elapsed & medium idle & we have never waited for medium access (IDEAL)
                    if(fullyIdle && !theRF.inUse()) {
                        theRF.transmit(datapck.getFrame());
                            state = WAITING_4_ACK;
                            break;
                    }
                case WAITING_BACKOFF: //////////////////////////////////////////////////////////////////////////////
                    try {
                        if(LinkLayer.debug ==1) output.println("Starting an ExBackoff CountDown from "+ waiting.getCD());

                        int remainingCountDown = waiting.BackoffWindow();
                        setBackoff = false; //WE DON'T WANT TO RERANDOMIZE COUNTDOWN

//                        if(LinkLayer.debug ==1)output.println("Remaining CountDown "+ remainingCountDown); //todo: remove output when working as intended

                        if (remainingCountDown != 0) { //Line interrupted while waiting backoff go back to waiting 4 med access
                            state = WAITING_4_MED_ACCESS;
                            if(LinkLayer.debug ==1) output.println("CountDown was Interrupted");
                            break;
                        }

                        //WE CAN ACTUALLY TRANSMIT
                        theRF.transmit(datapck.getFrame());
                        state = WAITING_4_ACK;
                        break;
                    } catch (InterruptedException e) {
                        continue;
                    }
                case WAITING_4_ACK: ////////////////////////////////////////////////////////////////////////////////
                    if((short)datapck.getDest()==-1){ //Don't expect an ACK for a broadcast
                        if(LinkLayer.debug==1) output.println("Broadcast was sent.");
                        resetTransmission();
                        state = WAITING_4_DATA;

                        //Calc if send Beacon or not
                        endT = LinkLayer.getClock(); //set the endT to clock and compare to startT
                        if(startT+ LinkLayer.getBeaconBackoff() < endT && LinkLayer.beaconsEnabled ) {
                            sendBeacon = true;
                        }
                        else sendBeacon = false;
                        break;
                    }
                    boolean ackReceived = false;
                    setBackoff = true; //We want to rerandomize countdown after we leave this state
                    if(LinkLayer.debug ==1) output.println("Transmitted packet #" + seqNum + ". Waiting Ack.");
                    //start a timer
                    //while(timer not elapsed && theRF.dataWaiting())

                    //Wait for Receiver to show us that an Ack Has been received, make sure it is correct one
                    waiting.setAckcd(waiting.getAcktimeout());
                    //long startTime = LinkLayer.getClock();
                    while(waiting.WaitForAck() > 0) {
                        if(ackFlag.get()>=0) { //when ackFlag is set, compare sequence numbers
                            if(LinkLayer.debug ==1) output.println("Sender Checking a possible ack...");
                            int ackseqNum = ackFlag.get();
                            if(ackseqNum==this.seqNum){ //ACK was for us!!!!!!!!!
                                ackReceived = true;
//                                if(LinkLayer.debug ==1) output.println("Ack " + seqNum + " Received");

                                break;
                            }
                        }
                    }
                    //long endTime = LinkLayer.getClock();
                    //output.println("took "+ (endTime-startTime) + " millis to get an ack");

                    //reset for next ack
                    ackFlag.set(-1);

                    //ACK received
                    if(ackReceived) {
//                        if(LinkLayer.debug==1) output.println("ACK RECEIVED");
                        if(LinkLayer.debug ==1) output.println("Ack " + seqNum + " Received");
                        resetTransmission();
                        waiting.resetWindow();
                        destToSequence.replace(dest,seqNum+1); //increment sequence number
                        LinkLayer.status=LinkLayer.TX_DELIVERED;
                        state = WAITING_4_DATA;

                        //Calc if send Beacon or not
                        endT = LinkLayer.getClock(); //set the endT to clock and compare to startT
                        if(startT+ LinkLayer.getBeaconBackoff() < endT && LinkLayer.beaconsEnabled) {
                            sendBeacon = true;
                        }
                        else sendBeacon = false;
                        break;

                    //no ACK received - need to retry and double contention window
                    } else {
                        if(LinkLayer.debug==1) output.println("Ack not received....need to retry transmission.");

                        //double contention window
                        int newWindow = ((waiting.getWindow()+1)*2)-1; //a bit of math for formatting
                        if(newWindow> RF.aCWmax) {
                            waiting.setWindow(RF.aCWmax);
                        } else {
                            waiting.setWindow(newWindow);
                        }

                        if(LinkLayer.debug==1)output.println("Doubled Window: [0.."+ waiting.getWindow()+ "]");

                        //upkeep
                        retry = true;
                        fullyIdle = false;
                        ++retryAttempts;
                        //Reached retry limit, reset everything
                        if (retryAttempts > theRF.dot11RetryLimit) {
                            if(LinkLayer.debug==1) output.println("Retry attempts reached. Discarding packet and resetting backoff window.");
                            resetTransmission();
                            waiting.resetWindow();
                            LinkLayer.status=LinkLayer.TX_FAILED;
                            destToSequence.replace(dest,seqNum+1); //increment sequence number
                        }
                        state = WAITING_4_DATA;
                        //Calc if send Beacon or not
                        endT = LinkLayer.getClock(); //set the endT to clock and compare to startT
                        if(startT+ LinkLayer.getBeaconBackoff() < endT && LinkLayer.beaconsEnabled) {
                            sendBeacon = true;
                        }
                        else sendBeacon = false;

                        break;

                    }
            }
        }
    }
}

