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
        long calc = theRF.aSIFSTime + theRF.clock(); //is it really this easy??
        try {
            while(true) {
                sleep(50);
                if (theRF.clock() >= calc) break; //if the clock is beyond our wait time then we must have waited that much time
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return calc;
    }

    public long DIFS(long end, RF theRF) throws InterruptedException {
        if (end > 0) { //don't calc new "end" point just continue until we did our hard time
            while(true) {
                sleep(50);
                if (theRF.clock() >= end) break; //if the clock is beyond our wait time then we must have waited that much time
            }
            return end;
        }

        //todo: DIFS = SIFS + 2 windows (aka 2 backoff slots)
        long calc = (SIFS(theRF) + (theRF.aSlotTime * 2)) + theRF.clock();
        while(true) {
            sleep(50);
            if (theRF.clock() >= calc) break; //if the clock is beyond our wait time then we must have waited that much time
        }
        // If sleep interrupted then save current time and when we resume continue with time as start
        return calc;
        //we minus by start because if the person got interrupted while waiting they should left off where they started
    }

    /**
     *
     * @param end   If end is greater than 0 then use this is the break case rather than calc new backoff time
     * @param theRF How we gather info for the vars like max min and clock
     * @return      The total time we waited.
     * @throws InterruptedException
     */
    public long BackoffWindow(int end,RF theRF) throws InterruptedException {
        if (end > 0) { //don't calc new "end" point just continue until we did our hard time
            while(true) {
                sleep(50);
                if (theRF.clock() >= end) break; //if the clock is beyond our wait time then we must have waited that much time
            }
            return end;
        }
        int max = theRF.aCWmax;
        int min = theRF.aCWmin;
        long ranBackoff = (int) (min + (Math.random() * max)); //might be wrong (probably is def wrong)
        long calc = (theRF.aSlotTime * ranBackoff)+theRF.clock();
        while(true) {
            sleep(50);
            if (theRF.clock() >= calc) break; //if the clock is beyond our wait time then we must have waited that much time
        }
        return calc;
    }
}
