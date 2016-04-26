package edu.utep.cs.cs4330.bluetoothdemo;


import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/** Allow peers to communicate with each other by sending and receiving
 * messages using a TCP/IP socket or a Bluetooth socket. It is assumed that
 * a connection is already established between the peers through the given
 * socket.
 * Each message is a single line of text, i.e., a sequence of characters
 * ended by the end-of-line character, and consists of a header and a body.
 * A message header identifies a message type and ends with a ":", e.g.,
 * "move:". A message body contains the content of a message and
 * if it contains more than one element, they are separated by a ",",
 * e.g., "4,5". There are a few different types of messages as listed below.
 *
 * <ul>
 *     <li>play: - request a new play</li>
 *     <li>play_ack: m, n - acknowledge a play request, where m (response)
 *         and n (turn) are either 0 or 1.
 *         If m is 1, the request is accepted; otherwise, it is rejected.
 *         If n is 1, the client starts first; otherwise, the server starts
 *         first.</li>
 *     <li>move: x, y - place a stone at a place x and y.</li>
 *     <li>move_ack: x, y - acknowledge a move message.</li>
 *     <li>quit: - quit a play.</li>
 *     <li>bye: - close the connection.</li>
 * </ul>
 *
 * The communication protocol is very simple.
 * The client makes a request to the server for a new play.
 * If the request is accepted by the server, the server determines
 * the turn and the play proceeds by sending and receiving a series
 * of move and move_ack messages until the game becomes over or
 * one of the players quit.
 *
 * <pre>
 *  Client        Server
 *    |------------>| play: - request for a new play
 *    |<------------| play_ack:1,1 - ack the play request
 *    |------------>| move:0,0 - client move
 *    |<------------| move_ack:0,0 - server ack
 *    |<------------| move:0,1 - server move
 *    |------------>| move_ack: client ack
 *    ...
 * </pre>
 *
 *  <pre>
 *  Client        Server
 *    |------------>| play: - request for a new play
 *    |<------------| play_ack:1,0 - ack the play request
 *    |<------------| move:0,0 - server move
 *    |------------>| move_ack:0,0 - client ack
 *    |------------>| move:0,1 - client move
 *    |<------------| move_ack: server ack
 *    ...
 * </pre>
 */
public class NetworkAdapter {

    public enum MessageType { CLOSE, QUIT, PLAY, PLAY_ACK, MOVE, MOVE_ACK, UNKNOWN };

    /** Listen to incoming messages. */
    public interface MessageListener {

        /** To be called when a message is received. The type of the received message
         * along with optional contents, say x and y, are provided as arguments.
         */
        void messageReceived(MessageType type, int x, int y);
    }

    private MessageListener listener;

    private BufferedReader in;
    private PrintWriter out;

    public NetworkAdapter(Socket socket) {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
        }
    }

    public NetworkAdapter(BluetoothSocket socket) {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
        }

    }

    /** Read messages from this network adapter and handle them. This method
     * blocks the calling method.
     */
    public void receiveMessages() {
        if(in==null){
            Log.i("Network Adapter", "in is null");
        }
        String line = null;
        try {
            while ((line = in.readLine()) != null) {
                parseMessage(line);
            }
        } catch (IOException e) {
        }
        notifyMessage(MessageType.CLOSE);
    }

    private void parseMessage(String msg) {
        if (msg.startsWith("bye:")) {
            notifyMessage(MessageType.CLOSE);
        } else if (msg.startsWith("quit:")) {
            notifyMessage(MessageType.QUIT);
        } else if (msg.startsWith("play_ack:")) {
            parsePlayAckMessage(msgBody(msg));
        } else if (msg.startsWith("play:")) {
            notifyMessage(MessageType.PLAY);
        } else if (msg.startsWith("move_ack:")) {
            parseMoveMessage(MessageType.MOVE_ACK, msgBody(msg));
        } else if (msg.startsWith("move:")){
            parseMoveMessage(MessageType.MOVE, msgBody(msg));
        } else {
            parseMoveMessage(MessageType.MOVE, msg);
        }
    }

    private String msgBody(String msg) {
        int i = msg.indexOf(':');
        if (i > -1) {
            msg = msg.substring(i + 1);
        }
        return msg;
    }

    private void parsePlayAckMessage(String msgBody) {
        String[] m = msgBody.split(",");
        int turn = 0;
        int response = Boolean.parseBoolean(m[0].trim()) ? 1 : 0;
        if (response == 1) {
            turn = Boolean.parseBoolean(m[1].trim()) ? 1 : 0;
        }
        notifyMessage(MessageType.PLAY_ACK, response, turn);
    }

    private void parseMoveMessage(MessageType type, String msgBody) {
        String[] m = msgBody.split(",");
        if (m.length >= 2) {
            try {
                int x = Integer.parseInt(m[0].trim());
                int y = Integer.parseInt(m[1].trim());
                notifyMessage(type, x, y);
                return;
            } catch (NumberFormatException e) {
            }
        }
        notifyMessage(MessageType.UNKNOWN);
    }

    // TODO: sync message sending, first-in first-out; use a single thread to send all
    private void writeMsg(final String msg) {
        if(out ==null){
            Log.i("Network Adapter", "Out is null");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                out.println(msg);
                out.flush();
            }
        }).start();
    }

    /** Write a bye message, i.e., terminate the connection. */
    public void writeBye() {
        if (out != null) {
            writeMsg("bye:");
        }
    }

    /** Write a quit (gg) message, i.e., quit the game. */
    public void writeQuit() {
        writeMsg("quit:");
    }

    public void writePlay() {
        writeMsg("play:");
    }

    public void writePlayAck(boolean response, boolean turn) {
        writeMsg("play_ack:" + response + "," + turn);
    }

    public void writeMove(int x, int y) {
        writeMsg("move:" + x + "," + y);
    }

    public void writeMoveAck(int x, int y) {
        writeMsg("move_ack:" + x + "," + y);
    }

    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    private void notifyMessage(MessageType type) {
        listener.messageReceived(type, 0, 0);
    }

    private void notifyMessage(MessageType type, int x, int y) {
        listener.messageReceived(type, x, y);
    }

    private void notifyMessage(MessageType type, int x) {
        listener.messageReceived(type, x, 0);
    }

}