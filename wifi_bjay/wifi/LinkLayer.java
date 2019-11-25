package wifi;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import rf.RF;

import static java.lang.Thread.sleep;


/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface 
{
	private static final int QUEUE_CAPACITY = 10; //todo: find better capacity
	private RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
    //queues for transmitting
	private ArrayBlockingQueue<Transmission> dataOutgoing = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);
	private ArrayBlockingQueue<Transmission> dataIncoming = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);
	private AtomicInteger ackFlag; //alerts sender thread of an ack and sends its sequence number
    public static int debug = 1; //0 is no output, 1 is full output



    /**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * @param ourMAC  MAC address
	 * @param output  Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.output = output;
		theRF = new RF(null, null);
		//TODO: start sender and receiver threads
        this.ackFlag = new AtomicInteger(-1);
		Sender send = new Sender(ourMAC,dataOutgoing,theRF,ackFlag,output);
		Receiver receive = new Receiver(ourMAC,theRF,dataIncoming,ackFlag,output);
		(new Thread(send)).start();
		(new Thread(receive)).start();
		output.println("LinkLayer: Constructor ran.");
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		output.println("LinkLayer: Sending "+len+" bytes to "+dest);
		byte[] splitArr = Arrays.copyOfRange(data,0,len);
		//theRF.transmit(data); //inside sender class now
		dataOutgoing.add(new Transmission(ourMAC,dest,splitArr));
		return len; //return the len of the amount of data
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		if(debug==1) output.println("LinkLayer: Waiting to receive.");


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
			if(debug == 1) output.println("Recv call interrupted.");
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
		output.println("LinkLayer: Sending command "+cmd+" with value "+val);
		return 0;
	}
}
