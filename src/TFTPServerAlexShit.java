import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPServerAlexShit {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "src/read/";
	public static final String WRITEDIR = "/home/username/write/";
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;
	DatagramPacket receivePackage;
	byte[] data;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServerAlexShit.class.getCanonicalName());
			System.exit(1);
		}
		try {
			TFTPServerAlexShit server= new TFTPServerAlexShit();
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

			final InetSocketAddress clientAddress= receiveFrom(socket, buf);
			if (clientAddress == null) /* If clientAddress is null, an error occurred in receiveFrom()*/
				continue;

			final StringBuffer requestedFile= new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread() {
			public void run() {
					try {
						DatagramSocket sendSocket= new DatagramSocket(0);
						sendSocket.connect(clientAddress);
						System.out.printf("%s request for from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",  clientAddress.getHostName(), clientAddress.getPort());

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

		receivePackage = new DatagramPacket(buf,buf.length);
		System.out.println("Receiving packet..");

		try{
			socket.receive(receivePackage);
		}catch (IOException e){
			e.printStackTrace();
		}

		data = receivePackage.getData();
		System.out.println("Server: Packet received:");
		System.out.println("From host: " + receivePackage.getAddress());
		System.out.println("Host port: " + receivePackage.getPort());
		System.out.println("Length: " + receivePackage.getLength());

		return  new InetSocketAddress(receivePackage.getAddress(),receivePackage.getPort());
	}

	private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
        System.out.println(requestedFile.toString());
        ByteBuffer wrap= ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();
		// We can now parse the request message for opcode and requested file as:
		requestedFile.append(new String(buf, 2, buf.length-2)); // where readBytes is the number of bytes read into the byte array buf.

		System.out.println("OPCODE " +opcode);
		return opcode;
	}

	private void HandleRQ(DatagramSocket sendSocket, String string, int opRrq) {
		String[] dong = string.split("\0");
		File file = new File(dong[0]);
		byte[] buffer = new byte[BUFSIZE-4];
		int length;


		if(opRrq == 1){
			FileInputStream in = null;
			DatagramPacket packet;
			try {
				in = new FileInputStream(file);
				short blockNumber = 1;
				while (true) {
					length = in.read(buffer);
					System.out.println("Length: "+length);

					packet = new DatagramPacket(buffer, blockNumber, length);

					if (sendPacket(packet, blockNumber++, sendSocket)) {
						System.out.println("Successfull send block" + blockNumber);
						return;
					}
					if (length < 512) {
						try {
							in.close();
						} catch (IOException e) {
							System.err.println("Trouble closing file.");
							break;
						}
					}
				}

			}catch (FileNotFoundException e) {
				e.getMessage();
				System.out.println("The requested file couldn't be found under : "+string);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else if(opRrq == 2){

		}
	}

	private boolean sendPacket(DatagramPacket packet, short blockNumber, DatagramSocket socket ){
		byte[] buffer = new byte[BUFSIZE];
		DatagramPacket receivingPacket = new DatagramPacket(buffer, buffer.length);
		short clientBlockNr=0;

		try {
			socket.send(packet);
			socket.setSoTimeout(5000);
			socket.receive(receivingPacket);

			ByteBuffer byteBuffer = ByteBuffer.wrap(receivingPacket.getData());
			System.out.println(new String(receivingPacket.getData()));
			short opcode = byteBuffer.getShort();

			if(opcode == OP_ERR){
				System.out.println("Client is not reachable");
			}else{
				clientBlockNr = byteBuffer.getShort();
			}

			if(clientBlockNr == blockNumber){
				System.out.println("Sent blocknumber equals ack-blocknumber");
				return true;
			}else{
				return false;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}
}