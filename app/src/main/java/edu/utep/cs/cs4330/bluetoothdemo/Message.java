package edu.utep.cs.cs4330.bluetoothdemo;

import android.util.Log;

/**
 * Created by juanrazo on 4/25/16.
 */
public class Message implements NetworkAdapter.MessageListener {


    public Message(){

    }
    
    @Override
    public void messageReceived(NetworkAdapter.MessageType type, int x, int y) {
        if(type == NetworkAdapter.MessageType.PLAY){
            Log.i("Message", "PLAY");
        }
    }
}
