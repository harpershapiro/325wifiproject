package wifi;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

import rf.RF;



/**
 * This is the 802.11~ Link Layer lovingly crafted by:
 * @author HARPER SHAPIRO & SKYLER LOFTUS
 */
public class LinkLayer implements Dot11Interface {
	//CONSTANTS
	public static final int QUEUE_CAPACITY = 4;
	public static final int SUCCESS=1,UNSPECIFIED_ERROR=2,RF_INIT_FAILED=3,
			TX_DELIVERED=4,TX_FAILED=5,BAD_ADDRESS=7,ILLEGAL_ARGUMENT=9,INSUFFICIENT_BUFFER_SPACE=10;
	public static final int MAX_SHORT_VALUE = 65535;

	//GLOBALS
	private static RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	//queues for transmitting
	private ArrayBlockingQueue<Transmission> dataOutgoing = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);
	private ArrayBlockingQueue<Transmission> dataIncoming = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);
	private AtomicInteger ackFlag; //alerts sender thread of an ack and sends its sequence number


	//upkeep and settings
	public static int debug; //0 is no output, 1 is full output
	public static AtomicLong clockOffset;
	public static int beaconBackoff;//how often we send beacons in ms user can change using console controls
	public static boolean beaconsEnabled;
	public static boolean backoffFixed;
	public static int status;



	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 *
	 * @param ourMAC MAC address
	 * @param output Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		if(ourMAC==-1){
			output.println("MAC address set to -1, a problematic value. Please don't do that.");
		}
		this.ourMAC = ourMAC;
		//settings and streams
		this.output = output;
		this.debug=-1;
		this.beaconBackoff = 10000; //10 second delay default
		this.beaconsEnabled=true;
		this.backoffFixed=false;
		//try to set up RF
		theRF = new RF(null, null);
		if(theRF==null) status = RF_INIT_FAILED;

		//set the vars for inter-thread communication
		this.ackFlag = new AtomicInteger(-1);
		this.clockOffset = new AtomicLong(0);


		//EXPERIMENTS
//		recvBeacon();
//		sendingBeacon();

		Sender send = new Sender(ourMAC, dataOutgoing, theRF, ackFlag, output);
		Receiver receive = new Receiver(ourMAC, theRF, dataIncoming, ackFlag, output, theRF.clock());
		(new Thread(send)).start();
		(new Thread(receive)).start();
		output.println("LinkLayer: Constructor ran.");
		this.status = SUCCESS;
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		//status upkeep
		if(len<0){
			status=ILLEGAL_ARGUMENT;
			return 0;
		}
		if(data==null){
			status=BAD_ADDRESS;
			return 0;
		}

		//attempt a send
		byte[] splitArr = Arrays.copyOfRange(data, 0, len);
		//theRF.transmit(data); //inside sender class now
		if(dataOutgoing.size()<QUEUE_CAPACITY){
			dataOutgoing.add(new Transmission(ourMAC, dest, splitArr));
		} else {
			status=INSUFFICIENT_BUFFER_SPACE;
			len=0;
		}
		output.println("LinkLayer: Sending " + len + " bytes to " + dest);
		return len; //return the len of the amount of data
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		if (debug == 1) output.println("LinkLayer: Waiting to receive.");
		if(t==null){
			status=BAD_ADDRESS;
			return 0;
		}

		try { //try to take Transmission and catch error if bad things occur
			Transmission receipt = dataIncoming.take(); //remove the data from the Queue and store the data for later
			//fill transmission object
			t.setBuf(receipt.getBuf());
			t.setDestAddr(receipt.getDestAddr());
			t.setSourceAddr(receipt.getSourceAddr());
		} catch (InterruptedException e) {
			if (debug == 1) output.println("LinkLayer: recv() call interrupted.");
			status=UNSPECIFIED_ERROR;
		}
		//this buffer contains headers+data. extract data, addresses, crc.
		return t.getBuf().length; //Does it need to return the length of data or hdr + data? ask brad
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		return status;
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command " + cmd + " with value " + val);
		if (cmd == 0) {
			output.println( "Command 1: value = [-1 -> debug output] [0 -> no debug output]" +
							"\nCommand 2: [0 Enable fixed backoff] [1 Enable random backoff]"+
							"\nCommand 3: [<= 0 Disable beacons] [> 0 Enabled beacons, Val is delay in seconds]");
			output.println("Current settings :\nDebug : "+debug+
							"\nBeacon enabled/delay :"+beaconsEnabled+"/"+beaconBackoff);
			//print status
			output.println("Status Code: " + status());
		}
		else if (cmd == 1) {
			if (val == -1) {
				output.println("Debug output...........activated");
				debug = 1;
			}
			if (val == 0) {
				output.println("Debug output...........vanquished");
				debug = 0;
			}
		}
		else if (cmd == 2) {
			if (val == 0) {output.println("Setting backoff to fixed"); backoffFixed = true; }
			else if (val == 1) {output.println("Setting backoff to random"); backoffFixed = false; }
		}
		else if (cmd == 3) { //how ofter we transmit beacons
			if (val <= -1) {
				output.println("Turning off beacon");
				beaconsEnabled = false;
			}
			else {
				output.println("Turning on beacon, second delay is now "+ val);
				beaconsEnabled = true;
				beaconBackoff = val*1000;
			}
		} else {
			output.println("Invalid command.");
		}
		return 0;
	}

	//sends beacon frames and determines avg time
	//RESULT ~2042 ms
	private void sendingBeacon(){
		//beacon frames bypass the queue so just directly send one
		int numTransmissions = 10;
		long ourTime = 0;
		long startTime = theRF.clock();
		for(int i=0;i<numTransmissions;i++) {
			//make new packet with beacon frame
			 ourTime = LinkLayer.getClock();
			byte[] data = new byte[8];
			for (int j = 7; j >= 0; j--) {
				data[j] = (byte)(ourTime & 0xFF);
				ourTime >>= 8;
			}
			Packet beacon = new Packet(2,0,0,-1,ourMAC,new byte[8],8);
			theRF.transmit(beacon.getFrame());
			//transmit
		}
		long endTime = theRF.clock();
		long avgTime = (endTime-startTime)/numTransmissions;
		System.out.println("AVG SENDER beacon transmission time: " + avgTime + "ms");
	}

	//receives beacon frames and finds average receipt time
	//RESULT: basically 0 ms
	private void recvBeacon(){
		CRC32 checksum = new CRC32();
		byte[] data;
		long ourTime = LinkLayer.getClock();
		byte[] ourTimes = new byte[8];
		for (int i = 7; i >= 0; i--) {
			ourTimes[i] = (byte)(ourTime & 0xFF);
			ourTime >>= 8;
		}

		int numTransmissions = 10;
		Packet beacon;
		beacon = new Packet(2,0,0,-1,ourMAC,ourTimes,8);
		byte[] rec_frame = beacon.getFrame();

		long startTime = theRF.clock();
		for(int i=0;i<numTransmissions;i++) {
			//Start CRC testing
			int extractCRC = Packet.extractCRC(rec_frame);
			byte[] crcBytes = Arrays.copyOfRange(rec_frame,0,rec_frame.length-Packet.CRC_BYTES);
			checksum.reset();
			checksum.update(crcBytes);
			if (extractCRC != (int)checksum.getValue()) {}
			//End CRC testing
			int frameType = Packet.extractcontrl(rec_frame,Packet.FRAME_TYPE);
			if(frameType ==1) {}

			//make new packet with beacon frame
			data = Arrays.copyOfRange(rec_frame,6, (rec_frame.length - Packet.CRC_BYTES)); //grab data from index 6 to len-4
			short dest = (short)Packet.extractdest(rec_frame);
			short src = (short)Packet.extractsrc(rec_frame);
			if(dest==(short)ourMAC | dest==-1) {
				if (frameType == 2 ) {
					long thereTime = 0;
					for (int j = 0; j < data.length; j++) //in theory this loop should only happen 8 times (long = 8 bytes)
					{
						thereTime = (thereTime << 8) + (data[j] & 0xff); //converting the byte array to a long value
					}
					//long ourTime = LinkLayer.getClock();
				}
			}

//			totalTime+=endTime-startTime;
		}
		long endTime = theRF.clock();
//		theRF.receive();

		//System.out.println("Endtime was " + endTime);
		long avgTime = (endTime-startTime)/numTransmissions;
		System.out.println("AVG RECV beacon transmission time: " + avgTime + "ms");

	}

	/**
	 * Adjusted clock time
	 * @return time
	 */
	public static long getClock() {
		return theRF.clock() + clockOffset.get();
	}

	/**
	 * Sets offset for Link layer
	 * @param n
	 */
	public static void setClockOffset(long n) {
		clockOffset.set(n);
	}

	/**
	 * Gets offset for link layer time
	 * @return offset
	 */
	public static long getClockOffset() {
		return clockOffset.get();
	}

	/**
	 * Gets amount of time between beacons
	 * @return amount of time
	 */
	public static int getBeaconBackoff() {
		return beaconBackoff;
	}
}
