import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TFTPServerAlexShit {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "src/read/";
	public static final String WRITEDIR = "src/write/";
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

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
			final InetSocketAddress clientAddress =
					receiveFrom(socket, buf);
			if (clientAddress == null) /* If clientAddress is null, an error occurred in receiveFrom()*/
				continue;

			final StringBuffer requestedFile = new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread() {
				public void run() {
					try {
						DatagramSocket sendSocket = new DatagramSocket(0);
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
	} // start
	/**
	 * Reads the first block of data, i.e., the request for action (read or write).
	 * @param socket socket to read from
	 * @param buf where to store the read data
	 * @return the Internet socket address of the client
	 */

	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {

		DatagramPacket receivePackage = new DatagramPacket(buf,buf.length);
		System.out.println("Receiving packet..");

		try{
			socket.receive(receivePackage);
		}catch (IOException e){
			e.printStackTrace();
			System.exit(1);
		}
		return  new InetSocketAddress(receivePackage.getAddress(),receivePackage.getPort());
	}

	private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
		ByteBuffer wrap= ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();
		// We can now parse the request message for opcode and requested file as:
		requestedFile.append(new String(buf, 2, buf.length - 2)); // where readBytes is the number of bytes read into the byte array buf.

		System.out.println("OPCODE " +opcode);
		return opcode;
	}

	private void HandleRQ(DatagramSocket sendSocket, String string, int opRrq) {
		String[] dong = string.split("\0");
		File file = new File(dong[0]);
		byte[] buffer = new byte[BUFSIZE-4];

		if(opRrq == 1){
			FileInputStream in = null;

			try {
				in = new FileInputStream(file);
				short blockNumber = 1;
				while (true) {
					int length = in.read(buffer);
					System.out.println("Length: "+length);

					DatagramPacket packet = dataPacket(blockNumber,buffer,length);

					if (sendPacket(packet, blockNumber++, sendSocket)) {
						System.out.println("Successfull send block" + blockNumber);
					}else {
						System.err.println("Error. Lost connection.");

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
				return;
			} catch (IOException e) {
				e.printStackTrace();

				return;
			}
		}else if(opRrq == 2){
			byte[] receiverBuffer = new byte[BUFSIZE];
			DatagramPacket receivingPacket = new DatagramPacket(buffer, buffer.length);
			FileOutputStream fileOutputStream = null;
			try {
				if(file.exists()){
					System.out.println("The file that should have been uploaded already exists");
					sendSocket.send(errorPacket("The file that should have been uploaded already exists"));

				}else{
						fileOutputStream =new FileOutputStream(file);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			short blockNum = 0;

			while (true) {
				DatagramPacket ackPacket = ackPacket(blockNum++);

                DatagramPacket packet = receivePacket(sendSocket,ackPacket,blockNum);
                try {
                    fileOutputStream.write(packet.getData(),4,packet.getData().length-4);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(packet.getLength()-4<512) {
                    try {
                        sendSocket.send(ackPacket(blockNum));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        fileOutputStream.close();
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
			}
            return;
		}
	}

	private DatagramPacket errorPacket(String errorMessage){
		ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
		buffer.putShort((short)5);	//ERROR OPCODE
		buffer.putShort((short)6);	//ERROR MESSAGE FOR FILE ALREADY EXISTS
		buffer.put(errorMessage.getBytes());
		buffer.putInt(0);			//YOU JUST NEED THAT 0 in the end
		return new DatagramPacket(buffer.array(),BUFSIZE);
	}


	private DatagramPacket dataPacket(short block, byte[] data, int length) {

		ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
		buffer.putShort((short)OP_DAT);
		buffer.putShort(block);
		buffer.put(data, 0, length);

		return new DatagramPacket(buffer.array(), 4+length);
	}
    private DatagramPacket ackPacket(short block) {

        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort((short)OP_ACK);
        buffer.putShort(block);

        return new DatagramPacket(buffer.array(), 4);
    } // ackPacket

    private DatagramPacket receivePacket(DatagramSocket sendSocket, DatagramPacket sendAck, short block){
        byte[] buffer = new byte[BUFSIZE];
        DatagramPacket receivingPacket = new DatagramPacket(buffer, buffer.length);

        try {
            sendSocket.send(sendAck);
            sendSocket.setSoTimeout(2000);
            sendSocket.receive(receivingPacket);

            //Get what opcode receiving packet had
            ByteBuffer buf = ByteBuffer.wrap(receivingPacket.getData());
            short opcode = buf.getShort();
            if(opcode == (short) OP_ERR) {
                System.out.println("Received OP_ERR opcode. Quitting");
                System.exit(1);
            } else {
                short receivedBlock = buf.getShort();
                if(receivedBlock == block) {
                    return receivingPacket;
                } else {
                    System.out.println("Blocknumbers not matching.");
                }
            }

        } catch(IOException e) {
            e.printStackTrace();
        }

        return null;
    }
	private boolean sendPacket(DatagramPacket packet, short blockNumber, DatagramSocket socket ){
		byte[] buffer = new byte[BUFSIZE];
		DatagramPacket receivingPacket = new DatagramPacket(buffer, buffer.length);
		short clientBlockNr=0;

		try {
			socket.send(packet);
			socket.setSoTimeout(10000);
			socket.receive(receivingPacket);

			ByteBuffer byteBuffer = ByteBuffer.wrap(receivingPacket.getData());
			short opcode = byteBuffer.getShort();
			if (opcode == OP_ERR) {
				System.err.println("Client is dead. Closing connection.");
				socket.close();
				System.exit(1);

			}else if (opcode == blockNumber) {
				return true;
			} else if (opcode == -1) {
				return false;}



		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("IO Error.");
		}


		return false;
	}



}



