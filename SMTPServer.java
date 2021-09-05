import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Iterator;
import java.util.Set;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.io.RandomAccessFile;

public class SMTPServer {
    private static Charset messageCharset = null;
	private static CharsetDecoder decoder = null;
    private static List<Integer> usedIDs = new ArrayList<Integer>();
    private static String writeIntoFile = "";
    private static int modus = 0;   // modus 0: receiving SMTP-Commands, modus 1: reading content
    private static List<String> receiver = new ArrayList<String>();
    private static String sender = "";
    private static String help = "";

    private static int order = 0;
    // order 0: erwarte helo
    // order 1: erwarte mail from
    // order 2: erwarte rcpt to
    // order 3: erwarte data
    // order 4: erwarte quit

    private static int extractrReceiver(String s) {
        if(s.length() - 11 == 0)
            return -1;

        int stop = s.length() - 1;
        int start = s.indexOf(":") + 2;
        String domain = s.substring(start, stop);
        if(domain.length() > 0){
            receiver.add(domain);
            return 1;
        }
        else
            return -1;
    }

    private static int extractSender(String s) {
        if(s.length() - 13 == 0){
            return -1;
        }
        int stop = s.length() - 1;
        int start = s.indexOf(":") + 2;
        sender = s.substring(start, stop);
        if(sender.length() > 0){
            receiver.clear();
            return 1;
        }
        else
            return -1;
    }

    private static int createRandomID() {
        int ID = -1;
        if(usedIDs.size() == 10000) {
            System.err.println("[-] Maximum limit of emails (10000) exceeded.");
            System.exit(1);
        }

        do {
            ID = (int) Math.floor(Math.random() * 10000);
        } 
        while(usedIDs.contains(ID));

        usedIDs.add(ID);
        return ID;
    }

    private static void determineHelpMsg(String operation) {
        if(operation.equals("help[ helo]\r\n")) help = "214 HELO <SP> <domain> <CRLF>";

        else if(operation.equals("help[ mail from]\r\n")) help = "214 MAIL <SP> FROM:<reverse-path> <CRLF>";

        else if(operation.equals("help[ rcpt to]\r\n")) help = "214 RCPT <SP> TO:<forward-path> <CRLF>";

        else if(operation.equals("help[ data]\r\n")) help = "214 DATA <CRLF>";

        else if(operation.equals("help\r\n")) help = "214 HELP [<SP> <string>] <CRLF>";
        
        else if(operation.equals("help[ quit]\r\n")) help = "214 QUIT <CRLF>";

        else help ="500 Syntax error, command unrecognized";
    }

    private static int checkHelo(String operation) {
        if(operation.startsWith("helo ") &&
            operation.endsWith("\r\n")){
            return operation.length() - 7 == 0 ?
                -3:
                1;
        }

        return 0;
    }

    private static boolean checkMailFrom(String operation) {
        return operation.startsWith("mail from: ") &&
            operation.endsWith("\r\n");
    }

    private static boolean checkRcptTo(String operation) {
        return operation.startsWith("rcpt to: ") &&
            operation.endsWith("\r\n");
    }

    private static boolean checkData(String operation) {
        return operation.equals("data\r\n");
    }

    private static boolean checkHelp(String operation) {
        return operation.startsWith("help");
    }

    private static boolean checkQuit(String operation) {
        return operation.equals("quit\r\n");
    }
    
    private static int heloMsg(String operation) {
        if(checkHelo(operation) == 1) {
            order = 1;
            return 0;
        }
        else if(checkHelo(operation) == -1)
            return -1;

        else if(checkHelo(operation) == -3)
            return -3;

        return checkMailFrom(operation) ||
            checkRcptTo(operation)||
            checkData(operation) ?
            7:
            -1;
    }

    private static int mailFromMsg(String operation, String s) {
        if(checkMailFrom(operation)) {
            if(extractSender(s) == -1)
                return -2;

            else{
                order = 2;
                return 1;
            }
        }

        return checkHelo(operation) == 1 ||
            checkRcptTo(operation) ||
            checkData(operation) ?
            7:
            -1;
    }

    private static int rcptToMsg(String operation, String s) {
        if(checkRcptTo(operation)) {
            if(extractrReceiver(s) == -1)
                return -2;

            else{
                order = 3;
                return 2;
            }
        }

        return checkHelo(operation) == 1 ||
            checkMailFrom(operation) ||
            checkData(operation) ?
            7:
            -1;
    }

    private static int dataMsg(String operation, String s) {    // multiple receivers
        if(checkData(operation)) {
            order = 1;
            return 3;
        }

        if(checkRcptTo(operation)) {
            if(extractrReceiver(s) == -1)
                return -2;

            else{
                order = 3;
                return 2;
            }
        }

        return checkHelo(operation) == 1 ||
            checkMailFrom(operation) ?
            7:
            -1;
    }

    private static int helpMsg(String operation) {
        if(checkHelp(operation)) {
            determineHelpMsg(operation);
            return 4;
        }

        return -1;
    }

    private static int quitMsg(String operation, String s) {
        if(checkQuit(operation))
            return 5;


        if(checkMailFrom(operation) && order == 1) {
            if(extractSender(s) == -1)
                return -2;

            else{
                order = 2;
                return 1;
            }
        }

        return -1;
    }

    private static int determineOperation(String s) {
        String operation = s.toLowerCase();
        int opCode = -1;

        if((opCode = helpMsg(operation)) == 4 ||
            opCode == 7)
            return opCode;

        else if((opCode = quitMsg(operation, s)) == 5 ||
                opCode == 1 ||
                opCode == 7)
            return opCode;


        else if(order == 0 &&
                ((opCode = heloMsg(operation)) == 0 ||
                opCode == 7))
            return opCode;

        else if(order == 1 &&
                ((opCode = mailFromMsg(operation, s)) == 1 ||
                opCode == 7))
            return opCode;

        else if(order == 2 &&
                ((opCode = rcptToMsg(operation, s)) == 2 ||
                opCode == 7))
            return opCode;

        else if(order == 3 &&
                ((opCode = dataMsg(operation, s)) == 3 ||
                opCode == 2 ||
                opCode == 7))
            return opCode;
        else if(order == 4){
            return 7;
        }

        return opCode;
    }

    private static void writeEmailIntoFile(String data, String sender, String receiver) {
        String currentDirectory = null;
        Path path = null;
        Path file = null;

        ByteBuffer buffer = ByteBuffer.allocate(8192);
        buffer.clear();

        int randomID = createRandomID();

        try {
            currentDirectory = System.getProperty("user.dir");
        } catch(SecurityException e) {
            System.err.println("[-] Access to CWD denied.");
            System.exit(1);
        } 

        path = Paths.get(currentDirectory).resolve(receiver);
        file = path.resolve(sender + "_" + randomID + ".txt");
        
        if(!(Files.exists(path))) {
            try {
                Files.createDirectories(path);
            } catch(IOException e) {
                System.err.println("[-] Creating directory failed.");
                System.exit(1);
            }
        }
        
        if(!(Files.exists(file))) {
            try {
                Files.createFile(file);
            } catch(IOException e) {
                System.err.println("[-] Creating file failed.");
                System.exit(1);
            }
        }
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(file.toString(), "rw");
            FileChannel fileChannel = randomAccessFile.getChannel();

            buffer.put(data.substring(0, data.length()).getBytes());
            buffer.flip();

            while(buffer.hasRemaining()) {
                fileChannel.write(buffer);
            }

            fileChannel.close();
        } catch(IOException e) {
            e.printStackTrace();
            System.err.println("[-] Couldn't not write data into file.");
        }
    }

    public static void main(String[] args) {
        Selector selector = null;

        try {
			messageCharset = Charset.forName("US-ASCII");
		} catch(UnsupportedCharsetException uce) {
			System.err.println("[-] Cannot create charset for this application. Exiting...");
			System.exit(1);
		}

        decoder = messageCharset.newDecoder();

        try {
			selector = Selector.open();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(8000));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        while(true)
        {
            try {
                if(selector.select() == 0)
                    continue;
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while(iter.hasNext()) 
            {
                SelectionKey key = iter.next();

                try 
                {
                    ByteBuffer buf = ByteBuffer.allocate(8192);
                    CharBuffer charBuf = null;

                    if(key.isAcceptable()) 
                    {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                        try {
                            client.write(ByteBuffer.wrap("220 LOCALHOST Simple Mail Transfer Service Ready\r\n".getBytes()));
                        } catch(IOException e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }

                    if(key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        String c = "";
                        String s = "";
                        int indexToCut;
                        int opCode;
                        
                        while(true) {
                            try {
                                channel.read(buf);
                                buf.flip();
                                charBuf = decoder.decode(buf);
                                
                                c = charBuf.toString();

                                if(modus == 0) {
                                    s = s.concat(c);
                                    if(s.contains("\r\n"))
                                        break;

                                } else {
                                    writeIntoFile = writeIntoFile.concat(c);
                                    if(writeIntoFile.contains("\r\n.\r\n"))
                                        break;
                                }

                                buf.clear();

                            } catch (CharacterCodingException e) {
                                System.err.println("[-] Couldn't read data from buffer.");
                                ByteBuffer response = ByteBuffer.allocate(1000);
                                response = ByteBuffer.wrap("500 Syntax error, command unrecognized\r\n".getBytes());
                                            channel.write(response);
                                continue;
                            }
                        }

                        if(modus == 0) {
                            opCode = determineOperation(s);

                            ByteBuffer response = ByteBuffer.allocate(8192);

                            switch(opCode) {
                                case 7:
                                        try {
                                            response = ByteBuffer.wrap("503 Bad sequence of commands\r\n".getBytes());
                                            channel.write(response);

                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case 0: 
                                        try {
                                            response = ByteBuffer.wrap("250 LOCALHOST\r\n".getBytes());
                                            channel.write(response);
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case 1:
                                        try {
                                            response = ByteBuffer.wrap("250 OK\r\n".getBytes());
                                            channel.write(response);
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case 2:
                                        try {
                                            response = ByteBuffer.wrap("250 OK\r\n".getBytes());
                                            channel.write(response);
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case 3:
                                        try {
                                            response = ByteBuffer.wrap("354 Start mail input; end with <CRLF>.<CRLF>\r\n".getBytes());
                                            channel.write(response);

                                            modus = 1;
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case 4:
                                        try {
                                            String rawResponse = help + "\r\n";
                                            response = ByteBuffer.wrap(rawResponse.getBytes());
                                            channel.write(response);
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case 5:
                                        try {
                                            response = ByteBuffer.wrap("221 LOCALHOST Service closing transmission channel\r\n".getBytes());
                                            channel.write(response);
                                            channel.close();
                                            order = 0;
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case -2:
                                        try {
                                            response = ByteBuffer.wrap("553 Requested action not taken: mailbox name not allowed\r\n".getBytes());
                                            channel.write(response);
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                case -3:
                                        try {
                                            response = ByteBuffer.wrap("501 Syntax error in parameters or arguments\r\n".getBytes());
                                            channel.write(response);
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                                default: 
                                        try {
                                            response = ByteBuffer.wrap("500 Syntax error, command unrecognized\r\n".getBytes());
                                            channel.write(response);
                                        } catch(IOException e) {
                                            e.printStackTrace();
                                            System.exit(1);
                                        }
                                        break;
                            }
                        } else {
                            if(writeIntoFile.contains("\r\n.\r\n")) {
                                modus = 0;
                                indexToCut = writeIntoFile.indexOf("\r\n.\r\n");

                                writeIntoFile = writeIntoFile.substring(0, indexToCut);

                                for(String r : receiver)
                                    writeEmailIntoFile(writeIntoFile, sender, r);

                                try {
                                    channel.write(ByteBuffer.wrap("250 OK\r\n".getBytes()));
                                    writeIntoFile = "";
                                } catch(IOException e) {
                                    e.printStackTrace();
                                    System.exit(1);
                                }
                            }
                        }
                    }
                } catch(IOException e) {
					e.printStackTrace();
					System.exit(1);
				}

                iter.remove();
            }
        }
    }
}
