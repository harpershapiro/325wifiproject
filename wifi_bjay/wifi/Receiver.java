package wifi;

public class Receiver implements Runnable {
    private int mac;

    public Receiver(int mac){
        this.mac = mac;
    }

    public byte[] getData(){
        return null;
    }

    @Override
    public void run(){
        //receive
    }

}
