import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

/**
 * Created by Ludde on 2016-03-03.
 */
public class TFTPErrorHandler {


    public static final int BUFSIZE = 516;
    private final short OP_ERR = 5;
    private final byte zeroByte = 0;

    public static final String[] errcodes = {"Not defined", "File not found.", "Access violation.",
            "Disk full or allocation exceeded.", "Illegal TFTP operation.",
            "Unknown transfer ID.", "File already exists.",
            "No such user."};

    private DatagramSocket sendSocket;

    public TFTPErrorHandler(DatagramSocket sendSocket) {
        this.sendSocket = sendSocket;
    }

    public void sendError(short errorCode) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort((OP_ERR));	//ERROR OPCODE
        buffer.putShort(errorCode);

        String errorMessage = errcodes[errorCode];

        buffer.put(errorMessage.getBytes());


        buffer.putInt(0);			//YOU JUST NEED THAT 0 in the end
        DatagramPacket errorPacket = new DatagramPacket(buffer.array(),BUFSIZE);
        try {
            sendSocket.send(errorPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



}
