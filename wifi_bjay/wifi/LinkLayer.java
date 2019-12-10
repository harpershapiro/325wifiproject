package wifi;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import rf.RF;

import static java.lang.Thread.sleep;


/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface {
	public static final int QUEUE_CAPACITY = 4;
	private static RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	//queues for transmitting
	private ArrayBlockingQueue<Transmission> dataOutgoing = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);
	private ArrayBlockingQueue<Transmission> dataIncoming = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);
	private AtomicInteger ackFlag; //alerts sender thread of an ack and sends its sequence number
	public static int debug = 1; //0 is no output, 1 is full output
	public static AtomicLong clockOffset;
	public static int beaconBackoff = 10000; //how often we send beacons in ms user can change using console controls



	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 *
	 * @param ourMAC MAC address
	 * @param output Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.output = output;
		theRF = new RF(null, null);
		//TODO: start sender and receiver threads
		this.ackFlag = new AtomicInteger(-1);
		this.clockOffset = new AtomicLong(0);

		//EXPERIMENTS
		//recvBeacon();
		//sendingBeacon();

		Sender send = new Sender(ourMAC, dataOutgoing, theRF, ackFlag, output);
		Receiver receive = new Receiver(ourMAC, theRF, dataIncoming, ackFlag, output, theRF.clock());
		(new Thread(send)).start();
		(new Thread(receive)).start();
		output.println("Starting beacon test...");

		output.println("LinkLayer: Constructor ran.");
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		byte[] splitArr = Arrays.copyOfRange(data, 0, len);
		//theRF.transmit(data); //inside sender class now
		if(dataOutgoing.size()<QUEUE_CAPACITY){
			dataOutgoing.add(new Transmission(ourMAC, dest, splitArr));
		} else {
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


		try { //try to take Transmission and catch error if bad things occur
			Transmission receipt = dataIncoming.take(); //remove the data from the Queue and store the data for later
			//fill transmission object
			t.setBuf(receipt.getBuf());
			t.setDestAddr(receipt.getDestAddr());
			t.setSourceAddr(receipt.getSourceAddr());
			//fill ACK packet to transmit
			if (t.getDestAddr() != -1) { //if it's not equal to -1 then we should send ACK
//				byte[] emptyData = new byte[0];
				//Transmission buildAck = new Transmission(t.getDestAddr(), t.getSourceAddr(), emptyData);
//				Packet ack_pck = new Packet(001,0,0,t.getSourceAddr(),t.getDestAddr(),emptyData,emptyData.length);
				//todo:wait SIFS then immediately transmit Need to find how to calculate SIFS (done? in new Wait class)
//				theRF.transmit(ack_pck.getFrame()); //should transmit the packet made above to the "sender" with ACK and empty data
			}
			//End ACK related things
		} catch (InterruptedException e) {
			if (debug == 1) output.println("Recv call interrupted.");
		}
		output.println(t.getBuf().length);
		//this buffer contains headers+data. extract data, addresses, crc.
		return t.getBuf().length; //Does it need to return the length of data or hdr + data? ask brad
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		output.println("LinkLayer: Faking a status() return value of 0");
		return 0;
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command " + cmd + " with value " + val);
		if (cmd == 0) {
			output.println("Command 1: value = [-1 -> debug output] [0 -> no debug output]");
		}
		if (cmd == 1) {
			if (val == -1) {
				output.println("Full debug output...........activated");
				debug = 1;
			}
			if (val == 0) {
				output.println("Debug output...........vanquished");
				debug = 0;
			}
		}
		return 0;
	}

	//sends beacon frames and determines avg time
	private void sendingBeacon(){
		//beacon frames bypass the queue so just directly send one
		long startTime = theRF.clock();
		int numTransmissions = 10;
		for(int i=0;i<numTransmissions;i++) {
			//make new packet with beacon frame
			Packet beacon = new Packet(2,0,0,-1,ourMAC,new byte[0],0);
			theRF.transmit(beacon.getFrame());
			//transmit
		}
		long endTime = theRF.clock();
		long avgTime = (endTime-startTime)/numTransmissions;
		System.out.println("AVG SENDER beacon transmission time: " + avgTime + "ms");

	}

	private void recvBeacon(){
		//beacon frames bypass the queue so just directly send one
//		data = Arrays.copyOfRange(rec_frame,6, (rec_frame.length - Packet.CRC_BYTES)); //grab data from index 6 to len-4
//		short dest = (short)Packet.extractdest(rec_frame);
//		output.println("Receiver got a data frame sent for " + dest);
//		short src = (short)Packet.extractsrc(rec_frame);
		byte[] data;
		long ourTime = LinkLayer.getClock();
		byte[] ourTimes = new byte[8];
		for (int i = 7; i >= 0; i--) {
			ourTimes[i] = (byte)(ourTime & 0xFF);
			ourTime >>= 8;
		}

		int numTransmissions = 10;
		Packet beacon;

		int totalTime = 0;
		for(int i=0;i<numTransmissions;i++) {
			//transmit a packet without timing
			beacon = new Packet(2,0,0,-1,ourMAC,ourTimes,8);
			theRF.transmit(beacon.getFrame());

			//time the receiving portion
			long startTime = theRF.clock();
			byte[] rec_frame = theRF.receive();
			System.out.println("Received something i="+i);
			int frameType = Packet.extractcontrl(rec_frame,Packet.FRAME_TYPE);
			if(frameType ==1) {}
			//make new packet with beacon frame
			data = Arrays.copyOfRange(rec_frame,6, (rec_frame.length - Packet.CRC_BYTES)); //grab data from index 6 to len-4
			short dest = (short)Packet.extractdest(rec_frame);
			short src = (short)Packet.extractsrc(rec_frame);
			if(dest==(short)ourMAC | dest==-1) {
				if (frameType == 2 ) {
					//todo: Grab data to get "thereTime" value to compare to our own
					long thereTime = 0;
					for (int j = 0; j < data.length; j++) //in theory this loop should only happen 8 times (long = 8 bytes)
					{
						thereTime = (thereTime << 8) + (data[j] & 0xff); //converting the byte array to a long value
					}
					//long ourTime = LinkLayer.getClock();
				}
			}
			long endTime = theRF.clock();
			totalTime+=endTime-startTime;
		}
//		theRF.receive();

		//System.out.println("Endtime was " + endTime);
		long avgTime = (totalTime)/numTransmissions;
		System.out.println("AVG RECV beacon transmission time: " + avgTime + "ms");

	}

	public static long getClock() {
		return theRF.clock() + clockOffset.get();
	}

	public static void setClockOffset(long n) {
		clockOffset.set(n);
	}

	public static long getClockOffset() {
		return clockOffset.get();
	}

	public static int getBeaconBackoff() {
		return beaconBackoff;
	}
}
