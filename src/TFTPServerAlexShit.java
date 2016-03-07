import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TFTPServerAlexShit {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "read/";
	public static final String WRITEDIR = "write/";
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

    private TFTPErrorHandler tftpErrorHandler;
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
                        tftpErrorHandler = new TFTPErrorHandler(sendSocket);

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

		return opcode;
	}

	private void HandleRQ(DatagramSocket sendSocket, String string, int opRrq) {
		String[] splitRequest = string.split("\0");
        //Splits request into : filepath requested, mode, and size.

		File file = new File(splitRequest[0]);
        String mode = splitRequest[1];
        if(!mode.equals("octet")) {
            tftpErrorHandler.sendError(4);
            return;
        }
		byte[] buffer = new byte[BUFSIZE-4];

		if(opRrq == 1){
            FileInputStream in;

			try {
				in = new FileInputStream(file);
				short blockNumber = 1;
				while (true) {
					int length = in.read(buffer);
					System.out.println("Length: "+length);
                    if(length==-1) {
                        length = 0;
                    }
                        DatagramPacket packet = dataPacket(blockNumber, buffer, length);

                        if (sendPacket(sendSocket, packet, blockNumber++)) {
                            System.out.println("Successfull send block" + blockNumber);
                        } else {
                            System.err.println("Error. Lost connection.");
                            return;
                        }

					if (length < 512) {
						try {
							in.close();
                            break;
						} catch (IOException e) {
							System.err.println("Trouble closing file.");
							break;
						}
					}
				}

			}catch (FileNotFoundException e) {
				tftpErrorHandler.sendError(1);
				return;
			} catch (IOException e) {
				e.printStackTrace();
                tftpErrorHandler.sendError(2);              //Sends "Access violation" after checking for file doesnt exist error.
				return;
			}
            return;
		}else if(opRrq == 2){
			byte[] receiverBuffer = new byte[BUFSIZE];
			DatagramPacket receivingPacket = new DatagramPacket(buffer, buffer.length);
			FileOutputStream fileOutputStream = null;
			try {
				if(file.exists()){
                    tftpErrorHandler.sendError(6);
                    return;
				}else{
						fileOutputStream =new FileOutputStream(file);
				}
			} catch (IOException e) {
				e.printStackTrace();
                tftpErrorHandler.sendError(7);              //Sends "No such user" after checking for file already exists error.
			}

			short blockNum = 0;

			while (true) {
                DatagramPacket ackPacket = ackPacket(blockNum++);

                DatagramPacket packet = receivePacket(sendSocket, ackPacket, blockNum);       //if error, returns null.
                if (packet == null) {
                    try {
                        fileOutputStream.close();                                              //If an error occured in receiving it is canceled, and we delete the incomplete file.
                        Files.delete(Paths.get(file.getPath()));
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fileOutputStream.write(packet.getData(), 4, packet.getData().length - 4);
                } catch (IOException e) {

                    e.printStackTrace();
                }
                if (packet.getLength() - 4 < 512) {
                        try {
                            sendSocket.send(ackPacket(blockNum));
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
    }

    private DatagramPacket receivePacket(DatagramSocket sendSocket, DatagramPacket sendAck, short block){
        byte[] buffer = new byte[BUFSIZE];
        DatagramPacket receivingPacket = new DatagramPacket(buffer, buffer.length);
		int retry=1;

		while(true){

			if(retry > 5){
				System.err.println("The client is not responding");
				return null;
			}
			try {
				sendSocket.send(sendAck);
				sendSocket.setSoTimeout(2000);
				sendSocket.receive(receivingPacket);

            //Get what opcode receiving packet had
            ByteBuffer buf = ByteBuffer.wrap(receivingPacket.getData());
            short opcode = buf.getShort();
            if(opcode == (short) OP_ERR) {
                tftpErrorHandler.sendError(0);
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
			retry++;
		}
    }
	private boolean sendPacket(DatagramSocket socket, DatagramPacket packet, short blockNumber) {
		byte[] buffer = new byte[BUFSIZE];
		DatagramPacket receivingPacket = new DatagramPacket(buffer, buffer.length);
		int retry = 1;


		while (true) {
			if (retry > 5) {
				System.err.println("The client is not responding");
				return false;
			}
			try {
				socket.send(packet);
				socket.setSoTimeout(10000);
				socket.receive(receivingPacket);

				ByteBuffer byteBuffer = ByteBuffer.wrap(receivingPacket.getData());
				short opcode = byteBuffer.getShort();
				short receivedBlocknr = byteBuffer.getShort();
				if (opcode == OP_ERR) {
					tftpErrorHandler.sendError(0);
				} else if (receivedBlocknr == blockNumber) {
					return true;
				} else if (receivedBlocknr == -1) {
					return false;
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("IO Error.");
			}


			return false;
		}

	}

}



