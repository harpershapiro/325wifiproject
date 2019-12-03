package wifi;

import rf.RF;

import static java.lang.Thread.dumpStack;
import static java.lang.Thread.sleep;


public class Wait { //A class with all the wait's we will use for this project
    private int countDown;
    private int window;
    private int acktimeout; //this is the number of slots of size ACK_TIMEOUT_MILLIS for ack countdowns
    private int ackcd;      //the var we count down how much we have slepted so far
    private RF theRF;

    public static final int ACK_TIMEOUT_MILLIS = 50;
    public Wait (RF theRF, int window,int acktimeout) { //todo: make an wait Object for threads to call upon to wait
        this.countDown = 0; //default to 0
        this.theRF = theRF;
        this.window = window;
        this.acktimeout = acktimeout;
        this.ackcd = acktimeout;
    }

    public int getWindow() {
        return window;
    }
    public int getCD() {
        return countDown;
    }


    public void setWindow(int window) {
        this.window = window;
    }

    public void setRanBackoff() {
        this.countDown = (int) (Math.random() * (window+1));
    }

    public void resetWindow(){
        this.window = RF.aCWmin;
    }

    public void setAcktimeout(int setAcktimeout) {
        this.acktimeout = setAcktimeout;
    }

    public void setAckcd(int setAckcd) {
        this.ackcd = setAckcd;
    }

    public int getAcktimeout() {
        return acktimeout;
    }

    public int WaitForAck() {
        try {
            sleep(ACK_TIMEOUT_MILLIS); //wait A slot time amount
        } catch (InterruptedException e) {
            e.printStackTrace();
            }
        ackcd--;           //then reduce the remaining fake "window" via countDown--
        return ackcd;
    }


    /**
     * @return an int of how long to wait. (the wait can happen here or where it is called)
     */
    public int SIFS() throws InterruptedException { //todo: remove the clock based waiting (this and DIFS) (done)
        //todo: calcSifs SIFS wait timer (done)
        sleep(theRF.aSIFSTime);
        return theRF.aSIFSTime; //Return how long we waited in total
    }

    /**
     *
     * @return  how long we waited in total
     * @throws InterruptedException
     */
    public int DIFS() throws InterruptedException {
        int calcDifs = theRF.aSIFSTime+(theRF.aSlotTime * 2);
        sleep((calcDifs));
        return calcDifs; //Return how long we waited in total
        //todo: DIFS = SIFS + 2 windows (aka 2 backoff slots) (done)
    }

    /**
     *
     * @return      The total time we waited.
     * @throws InterruptedException
     */
    public int BackoffWindow() throws InterruptedException {
        int singleSlotTime = theRF.aSlotTime;
        //setRanBackoff(); //do this in sender (done)
        System.out.println("BackOff calc'ed CountDown "+ countDown);
        int totalWait = countDown*singleSlotTime; //used for testing
        //todo: Add if interrupted logic as in save current countDown for later (might happen automatically with --;)
            while(countDown > 0) {
                System.out.println("sleeping at countdown= "+countDown);
                sleep(singleSlotTime); //wait A slot time amount
                countDown--;           //then reduce the remaining fake "window" via countDown--
                if (theRF.inUse()) {
                    break;//return countDown;

                }
            }
        return countDown; //Return how long we waited in total
    }
}
