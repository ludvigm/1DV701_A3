
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TFTPServer {
    public static final int TFTPPORT = 4970;
    public static final int BUFSIZE = 516;
    public static final String READDIR = "src/read/";
    public static final String WRITEDIR = "src/write/";
    public static final short OP_RRQ = 1;
    public static final short OP_WRQ = 2;
    public static final short OP_DAT = 3;
    public static final short OP_ACK = 4;
    public static final short OP_ERR = 5;

    public static void main(String[] args) {
        if (args.length > 0) {
            System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
            System.exit(1);
        }
        try {
            TFTPServer server= new TFTPServer();
            server.start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    private void start() throws SocketException {
        byte[] buf= new byte[BUFSIZE];
		
		/* Create socket */
        DatagramSocket socket= new DatagramSocket(null);
		
		/* Create local bind point */
        SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
        socket.bind(localBindPoint);

        System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

        while(true) {        /* Loop to handle various requests */
            final InetSocketAddress clientAddress=
                    receiveFrom(socket, buf);
            if (clientAddress == null) /* If clientAddress is null, an error occurred in receiveFrom()*/
                continue;

            final StringBuffer requestedFile= new StringBuffer();
            final int reqtype = ParseRQ(buf, requestedFile);

            new Thread() {
                public void run() {
                    try {
                        DatagramSocket sendSocket= new DatagramSocket(0);
                        sendSocket.connect(clientAddress);

                        System.out.printf("%s request for %s from %s using port %d\n",
                                (reqtype == OP_RRQ)?"Read":"Write", requestedFile.toString(),
                                clientAddress.getHostName(), clientAddress.getPort());

                        if (reqtype == OP_RRQ) {      /* read request */
                            requestedFile.insert(0, READDIR);
                            HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
                        }
                        else {                       /* write request */
                            requestedFile.insert(0, WRITEDIR);
                            HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);
                        }
                        sendSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }
    /**
     * Reads the first block of data, i.e., the request for action (read or write).
     * @param socket socket to read from
     * @param buf where to store the read data
     * @return the Internet socket address of the client
     */

    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {

        DatagramPacket receivePackage = new DatagramPacket(buf,buf.length);

        try{
            socket.receive(receivePackage);
        }catch (IOException e){
            e.printStackTrace();
        }

        return new InetSocketAddress(receivePackage.getAddress(),receivePackage.getPort());
    }

    private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
        ByteBuffer wrap= ByteBuffer.wrap(buf);
        short opcode = wrap.getShort();
        // We can now parse the request message for opcode and requested file as:
        requestedFile.append(new String(buf, 2, buf.length - 2)); // where readBytes is the number of bytes read into the byte array buf.
        System.out.println("OPCODE " + opcode);
        return opcode;
    }

    private void HandleRQ(DatagramSocket sendSocket, String string, int opRrq) {
        string = string.split("\0")[0];
        System.out.println("STRING: " + string);

        byte[] buf = new byte[BUFSIZE];

        FileInputStream in = null;
        if(opRrq == OP_RRQ) {
            while (true) {
                try {
                    File file = new File(string);
                    in = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                short blockNumber = 1;
                int length;
                try {
                    length = in.read(buf);
                    System.out.println("read " + length + " bytes.");
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }


                DatagramPacket sendPacket = createDataPacket(blockNumber, buf, length);

                //REceive stuff
                byte[] rec = new byte[BUFSIZE];
                DatagramPacket receivePacket = new DatagramPacket(rec,rec.length);

                try {
                    sendSocket.send(sendPacket);
                    System.out.println("sent a packet...");
                    Thread.sleep(2000);
                    sendSocket.receive(receivePacket);
                    System.out.println("RECEIVED: " + new String(receivePacket.getData()));
                    System.out.println(new String(rec));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }

                if (length < 512) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        System.err.println("Trouble closing file.");
                    }
                    break;
                }

            }
        }



    }

    private DatagramPacket createDataPacket(short blockNumber, byte[] data, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort(OP_DAT);
        buffer.putShort(blockNumber);
        buffer.put(data, 0, length);
        return new DatagramPacket(buffer.array(), 4+length);
    }
}



