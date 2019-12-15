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
    //todo: could add the time variable as global

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
        if(LinkLayer.backoffFixed) { //if true this backoff is fixed (always use max)
            countDown = window; //this is setting the backoff window to its max value always
        }
        else {
            this.countDown = (int) (Math.random() * (window+1)); //Choose a ran number between 0 and window size
        }
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
    public long SIFS() throws InterruptedException { //todo: remove the clock based waiting (this and DIFS) (done)
        //todo: calcSifs SIFS wait timer (done)
        long ourTime;
        //Wait Sifs
        sleep(theRF.aSIFSTime);
        //Now wait until time is within a 50ms window
        while(true) {
            ourTime = LinkLayer.getClock();
            if (ourTime % 100 == 50 || ourTime % 100 == 0) { //modding by 100 should only return last 2 digits
                break;  //break if the clock vlaue ends in a 50 or a 0 (logic should be every 50ish ms windows ie. 50 -> 0 -> 50 -> 0...)
            }
        }
        //ourTime = LinkLayer.getClock();
        if(LinkLayer.debug >= 1) System.out.println("After SIFS clock is :"+ ourTime); //todo: do we remove this or make it on the output in client windows?
        return ourTime; //Return current time (so whom ever called us can update there known time)
    }

    /**
     * Waits DIFS, rounding to 50ms slots
     * @return  how long we waited in total
     * @throws InterruptedException
     */
    public long DIFS() throws InterruptedException {
        long ourTime;
        int calcDifs = theRF.aSIFSTime+(theRF.aSlotTime * 2);
        sleep((calcDifs));
        while(true) {
            ourTime = LinkLayer.getClock();
            if (ourTime % 100 == 50 || ourTime % 100 == 0) { //modding by 100 should only return last 2 digits
                break;  //break if the clock vlaue ends in a 50 or a 0 (logic should be every 50ish ms windows ie. 50 -> 0 -> 50 -> 0...)
            }
        }
        //ourTime = LinkLayer.getClock();
        if(LinkLayer.debug >= 1) System.out.println("After DIFS clock is :"+ ourTime); //todo: do we remove this or make it on the output in client windows?
        return ourTime; //Return current time (so whom ever called us can update there known time)
        //todo: DIFS = SIFS + 2 windows (aka 2 backoff slots) (done)
    }

    /**
     *
     * @return      The total time we waited.
     * @throws InterruptedException
     */
    public int BackoffWindow() throws InterruptedException {
        int singleSlotTime = theRF.aSlotTime;
        long ourTime = 0;
        //setRanBackoff(); //do this in sender (done)
        System.out.println("BackOff calc'ed CountDown "+ countDown);     //todo: do we remove this or make it on the output in client windows?
        int totalWait = countDown*singleSlotTime; //used for testing
            while(countDown > 0) {
                System.out.println("sleeping at countdown= "+countDown); //todo: do we remove this or make it on the output in client windows?
                sleep(singleSlotTime); //wait A slot time amount
                countDown--;           //then reduce the remaining fake "window" via countDown--
                if (theRF.inUse()) {
                    break;//return countDown;

                }
            }
        while(true) {
            ourTime = LinkLayer.getClock();
            if (ourTime % 100 == 50 || ourTime % 100 == 0) { //modding by 100 should only return last 2 digits
                break;  //break if the clock vlaue ends in a 50 or a 0 (logic should be every 50ish ms windows ie. 50 -> 0 -> 50 -> 0...)
            }
        }
        //unsure if we return clock or countDown as we need to return countDown if we get intrupted so we dont loose that value
        if(LinkLayer.debug >= 1) System.out.println("After Backoff clock is :"+ ourTime); //todo: do we remove this or make it on the output in client windows?
        return countDown; //Return how long we waited in total
//        return LinkLayer.getClock(); //Return current time (so whom ever called us can update there known time)
    }
}
