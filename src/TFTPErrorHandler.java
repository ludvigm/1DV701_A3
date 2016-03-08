import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class TFTPErrorHandler {


    public static final int BUFSIZE = 516;

    public static final String[] errcodes = {"Not defined", "File not found.", "Access violation.",
            "Disk full or allocation exceeded.", "Illegal TFTP operation.",
            "Unknown transfer ID.", "File already exists.",
            "No such user."};

    private DatagramSocket sendSocket;

    public TFTPErrorHandler(DatagramSocket sendSocket) {
        this.sendSocket = sendSocket;
    }

    public void sendError(int errorCode) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort((short)5);	         //ERROR OPCODE
        buffer.putShort((short)errorCode);  //Put errorCode

        String errorMessage = errcodes[errorCode];
        buffer.put(errorMessage.getBytes());            //Put message according to errorcode

        buffer.put((byte) 0);			//end with a 0
        DatagramPacket errorPacket = new DatagramPacket(buffer.array(),BUFSIZE);
        try {
            sendSocket.send(errorPacket);
            System.out.println("Responded to client with error: " + errorCode + " - " + errorMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



}
