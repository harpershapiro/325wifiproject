package wifi;
import java.io.PrintWriter;
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
    private Packet datapck = null;
    private int retryAttempts;
    private int seqNum;
    private AtomicInteger ackFlag; //value of the sequence number of an ack received in receiver thread (shared)



    public Sender(int mac, ArrayBlockingQueue<Transmission> dataOutgoing, rf.RF theRF,AtomicInteger ackFlag, PrintWriter output){
        this.mac = mac;
        this.retry = false;
        this.fullyIdle = false;
        this.state = WAITING_4_DATA; //when thread is called start at waiting4data to start
        this.dataOutgoing = dataOutgoing;
        this.theRF = theRF;
        this.output = output;
        this.retryAttempts=0;
        this.seqNum=0;
        this.ackFlag = ackFlag;
    }

    public void resetTransmission(){
        fullyIdle = false;
        //set datapck to null
        datapck = null;
        retry = false;
        retryAttempts = 0;
        seqNum++;
    }

    @Override
    public void run(){
        //testing thread
//        while(true) {
//            System.out.println("Sender thread running.");
//            try {
//                Transmission data = dataOutgoing.take();
//                byte[] bytedata = data.getBuf();
//                Packet pck = new Packet(000,0,0,data.getDestAddr(),data.getSourceAddr(),bytedata,bytedata.length);
//                System.out.println("have created a packet pck : "+ pck);
//                //todo: check if idle before transmit and only send once it is Idle (is WITHIN scope of CP#2)
//                while((theRF.inUse())) {
//                    output.println("Channel In use Waiting 2 seconds");
//                    sleep(2000);
//                }
//                byte[] frame = pck.getFrame();
//                output.println("Last byte of data: " + frame[frame.length-5]);
//                theRF.transmit(frame); //Sent off frame to recv() to be picked up by Recv() class
//                System.out.println("have Transmitted the PCK");
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
        //Wait for something to arrive on the outgoing data queue
        //Data arrives:
            //Make packet
            //Check if medium idle
            //not idle:

                //wait until idle
                //channel is idle:
                    //wait DIFS
                    //channel still idle:
                        //wait exponential backoff time
                        //channel interrupted:
                            //save exponential timer state
                            //go back to wait DIFS step
                        //exponential timer finished:
                            //transmit frame
                            //wait for an ack
                            //ack received:
                                //set fully idle to false
                                //go back to beginning
                            //ack not received in time:
                                //up contention window
                                //set retry to true
                                //go back to beginning, new packet out of same data from before
                    //channel not still idle:
                        //back to waiting for channel to be idle


            //idle:
                //fullyIdle = true;
                //wait DIFS
                //time elapsed & medium idle:
                    //transmit packet
                    //wait for an ack
                    //ack received:
                        //set fully idle to false
                        //go back to beginning
                    //ack not received in time:
                        //up contention window
                        //set retry to true
                        //go back to beginning, new packet out of same data from before

        // switch statement using above comment as reference // beyond scope of CP#2 but good to look at.
        Wait waiting = new Wait(32,theRF.aCWmin,100);
        boolean setBackoff = true;
        while(true) {
            if (setBackoff) {
                waiting.setRanBackoff(); //each loop we set the Window backoff size to use this loop (we reloop when this changes anyway so its safe)
            }
            switch (state) {
                case WAITING_4_DATA:
                    //look for data to send
                    //OLD WAY OF KEEPING OLD DATA AROUND
                   /* Transmission data = dataOutgoing.peek();
                    if (data == null) {
//                        continue;
                        break;
                    }*/
                   //BRAND NEW TRANSMISSION
                   if(datapck==null) {
                       Transmission data; //holds what we grabbed from queue
                       //get next object
                       try {
                           data = dataOutgoing.take();//todo: remove this line, only need while constructing state machine
                       } catch (InterruptedException e) {
                           continue;
                       }

                       //create data packet from our newest transmission
                       this.datapck = new Packet(000, 0, seqNum, data.getDestAddr(), data.getSourceAddr(), data.getBuf(), data.getBuf().length);
                   }
                   //RETRY TRANMISSION
                   else {
                       datapck.setRetry(1);
                   }

                    //assign a new state depending on medium access
                    if (theRF.inUse()) { //if the Line is in use change to the corresponding state.
                        //we would set to false but it already set to false
                        state = WAITING_4_MED_ACCESS;
                        break;
                    }
                    else {
                        fullyIdle = true;
                        state = WAITING_DIFS;
                        break;
                    }

                case WAITING_4_MED_ACCESS:
//                    output.println("WAITING_4_MED_ACCESS:"); //todo: remove output when working as intended
//                    state=WAITING_4_DATA;
//                    break;
                    //check if RF in use, If false then line not in use wait DIFS
                if (theRF.inUse()) {
                        state = WAITING_4_MED_ACCESS; //keep waiting untail RF is not in use (false)
                        break;
                    }
                    else {
                        state = WAITING_DIFS;
                        //from difs state we wait have two options (1 wait4MedAcc, 2 backoff window)
                        break;
                    }
                    //below logic has been moved to DIFS or Backoff state
                    //then transmit packet set state to waiting4ack
//                    if (fullyIdle == true) {
//                        theRF.transmit(datapck.getFrame());
//                        state = WAITING_4_ACK;
//                        break;
//                    }
                    //else wait exponential backoff time
//                    else {
                            //channel interrupted:
                            //save exponential timer state
                            //set state to WAITING_4_MED_ACCESS again and loop
//                    }
                case WAITING_DIFS: //this will have the wait Object being used here
                    try {
//                        sleep(2000);
                        output.println("WAITIN_DIFS "+waiting.DIFS(theRF)); //todo: remove output when working as intended
                    } catch (InterruptedException e) {
                        continue;
                    }
                    //state=WAITING_4_DATA; //todo:remov when machine works
                    //time elapsed & medium is not idle
                    if (theRF.inUse()) { //true if line currently in use
                        state = WAITING_4_MED_ACCESS; //wait until line not in use
                        break;
                    }
                    //time elapsed & medium is idle & fullyIdle false
                    if (fullyIdle == false && !theRF.inUse()) { //good the line not in use start backoff
                                                                //If fullyIdle is false then must be right branch of MAC rules
                        state = WAITING_BACKOFF;
                        break;
                    }
                    //time elapsed & medium idle & fullyIdle
                    //IDEAL CASE
                    if(fullyIdle && !theRF.inUse()) { //left branch of MAC rules (best case)
                        theRF.transmit(datapck.getFrame());
                            state = WAITING_4_ACK;
                            break;
                    }
                case WAITING_BACKOFF: //almost always transmit when this is called
                    //timer elapsed in wait object
                    try {
                        output.println("Total CountDown "+ waiting.getCD());
                        int remainingCountDown = waiting.BackoffWindow(theRF);
                        setBackoff = false; //WE DON'T WANT TO RERANDOMIZE COUNTDOWN
                        output.println("Remaining CountDown "+ remainingCountDown); //todo: remove output when working as intended
                        if (remainingCountDown != 0) { //Line was in use while waiting backoff go back to waiting 4 med access
                            state = WAITING_4_MED_ACCESS;
                            output.println("CountDown was Interrupted");
                            break;
                        }
                        theRF.transmit(datapck.getFrame());
                        state = WAITING_4_ACK;
                        break;
                    } catch (InterruptedException e) {
                        //medium is accessed elsewhere
                        //state=WAITING_DIFS;
                        continue;
                        //break;
                    }
                case WAITING_4_ACK: //not a wait Object thing but an RF thingy
                    if((short)datapck.getDest()==-1){
                        resetTransmission();
                        state = WAITING_4_DATA;
                        break;
                    }
                    boolean ackReceived = false;
                    setBackoff = true; //We want to rerandomize countdown after we leave this state
                    output.println("Transmitted packet #" + seqNum + ". Waiting Ack.");
                    //start a timer
                    //while(timer not elapsed && theRF.dataWaiting())
                    waiting.setAckcd(waiting.getAcktimeout());
                    while(waiting.WaitForAck() > 0) { //todo: MOVE THIS INTO RECEIVER THREAD
                        if(ackFlag.get()>=0) { //when ackFlag is set, compare sequence numbers
                            output.println("Picking up a possible ack...");
                            //byte[] possibleAck = theRF.receive();
                            int seqNum = ackFlag.get();
                            //output.println("sender saw ackFlag, sequence number of " + seqNum);
                            //int dest = Packet.extractdest(possibleAck);
                            //ACK was for us!!!!!!!!!
                            if(seqNum==this.seqNum){
                                ackReceived = true;
                                output.println("AckRecv = "+ackReceived);
                                break;
                            }
                        }
                    }
                    ackFlag.set(-1);
                    //ack received
                    if(ackReceived) {
                        output.println("ACK RECEIVED");
                        resetTransmission();
                        waiting.resetWindow();
                        state = WAITING_4_DATA;
                        break;
                    //no ack received - might need to retry
                    } else {
                        output.println("Ack not received....going back to waiting for data");
                        waiting.setWindow(waiting.getWindow()*2);
                        output.println("Doubled Window to "+ waiting.getWindow());
                        retry = true;
                        ++retryAttempts;
                        if (retryAttempts > theRF.dot11RetryLimit) { //This is a full reset
                            resetTransmission();
                        }
                        state = WAITING_4_DATA;
                        break;
                        //ack not received in time:
                        //reset everything if retry limit is reached
                        //otherwise
                        //up contention window
                        //set retry to true
                        //increment retries

                        //go back to beginning, new packet out of same data from before
                    }



                    //maybe?
                    //here we will remove or take from the Queue depending on if Ack was received or retransmit
                    //break;
            }
//            System.out.println(state);
        }
    }
}

