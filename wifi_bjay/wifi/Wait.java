package wifi;

import rf.RF;

import static java.lang.Thread.sleep;


public class Wait { //A class with all the wait's we will use for this project
    private int countDown;
    private int window;
    public Wait (int countDown, int window) { //todo: make an wait Object for threads to call upon to wait
        this.countDown = countDown;
        this.window = window;
    }

    public int getWindow() {
        return window;
    }
    public int getCD() {
        return countDown;
    }

    public void setCountDown(int countDown) {
        this.countDown = countDown;
    }

    public void setWindow(int window) {
        this.window = window;
    }

    public void setRanBackoff() {
        this.countDown = (int) (Math.random() * window);
    }

    public void resetWindow(){
        this.window = RF.aCWmin;
    }


    /**
     * @param theRF is what we call to get info like SIFS time
     * @return an int of how long to wait. (the wait can happen here or where it is called)
     */
    public int SIFS(RF theRF) throws InterruptedException { //todo: remove the clock based waiting (this and DIFS) (done)
        //todo: calcSifs SIFS wait timer (done)
        int calcSifs = theRF.aSIFSTime; //is it really this easy? (guess so)
        sleep(calcSifs);
        return calcSifs; //Return how long we waited in total
    }

    /**
     *
     * @param theRF is what we call to get info like slot time and SIFS time
     * @return  how long we waited in total
     * @throws InterruptedException
     */
    public int DIFS(RF theRF) throws InterruptedException {
        int calcDifs = theRF.aSIFSTime+(theRF.aSlotTime * 2);
        sleep((calcDifs));
        return calcDifs; //Return how long we waited in total
        //todo: DIFS = SIFS + 2 windows (aka 2 backoff slots) (done)
    }

    /**
     *
     * @param theRF How we gather info for the vars like max min and clock
     * @return      The total time we waited.
     * @throws InterruptedException
     */
    public int BackoffWindow(RF theRF) throws InterruptedException {
        int singleSlotTime = theRF.aSlotTime;
        //setRanBackoff(); //do this in sender (done)
        System.out.println("BackOff calc'ed CountDown "+ countDown);
        int totalWait = countDown*singleSlotTime; //used for testing
        //todo: Add if interrupted logic as in save current countDown for later (might happen automatically with --;)
            while(countDown > 0) {
                sleep(singleSlotTime); //wait A slot time amount
                countDown--;           //then reduce the remaining fake "window" via countDown--
                if (theRF.inUse()) {
                    break;//return countDown;

                }
            }
        return countDown; //Return how long we waited in total
    }
}
