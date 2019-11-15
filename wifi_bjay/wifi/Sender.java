package wifi;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

import static java.lang.Thread.sleep;


public class Sender implements Runnable {
    private int mac;
    private boolean retry; //true if we are resending current packet
    private boolean fullyIdle; //true if we have never seen the channel busy during this transmission
    private String state;
    private  ArrayBlockingQueue<Transmission> dataOutgoing;
    private rf.RF theRF;
    private PrintWriter output;



    public Sender(int mac, ArrayBlockingQueue<Transmission> dataOutgoing, rf.RF theRF, PrintWriter output){
        this.mac = mac;
        this.retry = false;
        this.fullyIdle = false;
        this.state = "waiting4data"; //when thread is called start at waiting4data to start
        this.dataOutgoing = dataOutgoing;
        this.theRF = theRF;
        this.output = output;
    }

    @Override
    public void run(){
        //testing thread
        while(true) {
            System.out.println("Sender thread running.");
            try {
                Transmission data = dataOutgoing.take();
                byte[] bytedata = data.getBuf();
                Packet pck = new Packet(000,0,0,data.getDestAddr(),data.getSourceAddr(),bytedata,bytedata.length);
                System.out.println("have created a packet pck : "+ pck);
                //todo: check if idle before transmit and only send once it is Idle (is WITHIN scope of CP#2)
                while((theRF.inUse())) {
                    output.println("Channel In use Waiting 2 seconds");
                    sleep(2000);
                }
                byte[] frame = pck.getFrame();
                output.println("Last byte of data: " + frame[frame.length-5]);
                theRF.transmit(frame); //Sent off frame to recv() to be picked up by Recv() class
                System.out.println("have Transmitted the PCK");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
//        switch (state) {
//            case "waiting4data":
//                //wait until incoming data, once data comes check if idle or not and update state
//                //is idle set state to idle
//                //not idle set state to !idle
//                break;
//            case "idle":
//                //wait DFS then see if fullyIdle == true
//                //then transmit packet set state to waiting4ack
//                //else wait exponential backoff time
//                    //channel interrupted:
//                    //save exponential timer state
//                    //set state to !idle state again and loop
//                break;
//            case "!idle":
//                //wait until idle but also set fullyIdle = false
//                //then set state to idle
//                break;
//            case "waiting4ack":
//                //ack received:
//                    //set fully idle to false
//                    //go back to beginning
//                //ack not received in time:
//                    //up contention window
//                    //set retry to true
//                    //go back to beginning, new packet out of same data from before
//                break;
//            case "transmit":
//                //transmit frame
//                //set state to waiting4ack
//                break;
//        }
//        System.out.println(state);
    }
}

