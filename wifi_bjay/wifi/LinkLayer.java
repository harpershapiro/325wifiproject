package wifi;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

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
	ArrayBlockingQueue<Transmission> dataOutgoing = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);
	ArrayBlockingQueue<Transmission> dataIncoming = new ArrayBlockingQueue<Transmission>(QUEUE_CAPACITY);


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
		Sender send = new Sender(ourMAC,dataOutgoing,theRF);
		Receiver receive = new Receiver(ourMAC,theRF,dataIncoming);
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
		//theRF.transmit(data); //inside sender now
		dataOutgoing.add(new Transmission(ourMAC,dest,splitArr));
		System.out.println("Have added to dataOutcomming Queue");
		return len;
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		output.println("LinkLayer: Pretending to block on recv()");
		while(true); // <--- This is a REALLY bad way to wait.  Sleep a little each time through.
		// return 0;
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
