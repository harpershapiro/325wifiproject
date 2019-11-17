package wifi;

import rf.RF;

import static java.lang.Thread.sleep;

public class Wait { //A class with all the wait's we will use for this project

    /**
     * Calling SIFS will return an int of how many mill's to wait. (is also used in the calc of DIFS)
     * @param theRF
     * @return an int of how long to wait. (the wait can happen here or where it is called)
     */
    public long SIFS(RF theRF) {
        //todo: calc SIFS wait timer (maybe done)
        long calc = theRF.aSIFSTime; //is it really this easy??
        try {
            sleep(calc);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return calc;
    }

    public long DIFS(short start, RF theRF) {


        //todo: DIFS = SIFS + 2 windows (aka 2 backoff slots)
        long calc = SIFS(theRF) + (theRF.aSlotTime * 2);
        //If sleep interrupted then save current time and when we resume continue with time as start


        return calc-start;
        //we minus by start because if the person got interrupted while waiting they should left off where they started
    }
    public long BackoffWindow(int start,RF theRF) {
        int max = theRF.aCWmax;
        int min = theRF.aCWmin;
        long ranBackoff = (int) (min + (Math.random() * max));
        long calc = (int) theRF.aSlotTime * ranBackoff;
        return calc-start;
    }
}
