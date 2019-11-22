package wifi;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

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


    public Sender(int mac, ArrayBlockingQueue<Transmission> dataOutgoing, rf.RF theRF, PrintWriter output){
        this.mac = mac;
        this.retry = false;
        this.fullyIdle = false;
        this.state = WAITING_4_DATA; //when thread is called start at waiting4data to start
        this.dataOutgoing = dataOutgoing;
        this.theRF = theRF;
        this.output = output;
        this.retryAttempts=0;
        this.seqNum=0;
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
        while(true) {
            switch (state) {
                case WAITING_4_DATA:
                    //look for data to send. do not remove until ACKed.
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
                    output.println("REACHED WAITING$MEDACCESS");
                    state=WAITING_4_DATA;
                    break;
                    //wait DFS then see if fullyIdle == true
                    //then transmit packet set state to waiting4ack
                    //else wait exponential backoff time
                    //channel interrupted:
                    //save exponential timer state
                    //set state to !idle state again and loop

                case WAITING_DIFS: //this will have the wait Object being used here
                    output.println("WAITING DIFS");
                    try {
                        sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //state=WAITING_4_DATA; //todo:remov when machine works

                    //time elapsed & medium is not idle
                        //state = WAITING_4_MED_ACCESS;
                        // break;
                    //time elapsed & medium is idle & fullyIdle false
                        //state = WAITING_BACKOFF;
                        // break;
                    //time elapsed & medium idle & fullyIdle
                    //IDEAL CASE
                    if(fullyIdle & !theRF.inUse()) {
                        theRF.transmit(datapck.getFrame());
                        if(!theRF.inUse()){
                            state = WAITING_4_ACK;
                            break;
                        }
                    }
                case WAITING_BACKOFF:
                    //timer elasped in wait object
                        //state=WAITING_4_ACK;
                        //break;
                    //medium is accessed elsewhere
                        //state=WAITING_DIFS;
                    break;
                case WAITING_4_ACK: //not a wait Object thing but an RF thingy
                    boolean ackReceived = false;
                    output.println("Transmitted packet #" + seqNum + ". Waiting Ack.");
                    //start a timer
                    //while(timer not elapsed && theRF.dataWaiting())
                    int timer = 20; //TESTING TIMER>....................................................
                    while(timer>0){
                        if(theRF.dataWaiting()) {
                            byte[] possibleAck = theRF.receive();
                            int seqNum = Packet.extractcontrl(possibleAck,Packet.SEQ_NUM);
                            int dest = Packet.extractdest(possibleAck);
                            //ACK was for us!!!!!!!!!
                            if(seqNum==this.seqNum && dest==mac){
                                ackReceived = true;
                            }
                        }
                        //sleep a little
                        try {
                            sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        timer--;
                    }
                    //ack received:
                    if(ackReceived) {
                        //set fully idle to false
                        fullyIdle = false;
                        //set datapck to null
                        datapck = null;
                        //increment seq number
                        seqNum++;
                        state = WAITING_4_DATA;
                        break;
                    } else {
                        output.println("Ack not received....going back to waiting for data");
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

