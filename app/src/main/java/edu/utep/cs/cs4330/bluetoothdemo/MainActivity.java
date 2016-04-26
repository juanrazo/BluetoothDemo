package edu.utep.cs.cs4330.bluetoothdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {


    private BluetoothAdapter BA = BluetoothAdapter.getDefaultAdapter();
    private final static UUID MY_UUID = UUID.fromString("f5d23654-5558-40bc-ba2c-2277b1269274");
    private final int RECIEVE = 1;
    private final int SEND = 2;
    private int state = 0;
    private ConnectThread connectThread;
    private AcceptThread acceptThread;
    private NetworkAdapter networkAdapter;
    private Message listener;
    private Button send;

    public void turnBluetoothOff(View view){
        BA.disable();
        if(BA.isEnabled()){
            Toast.makeText(getApplicationContext(), "Bluetooth could not be disabled", Toast.LENGTH_LONG).show();
        }
        else{
            Toast.makeText(getApplicationContext(), "Bluetooth is off", Toast.LENGTH_LONG).show();
        }
    }

    public void findDiscoverableDevices(View view){
        Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        startActivity(i);
    }

    public void viewPairedDevices(View view){
        final Set<BluetoothDevice> pairedDevices = BA.getBondedDevices();

        ListView pairedDeviceslistView = (ListView) findViewById(R.id.pairedDeviceslistView);

        final ArrayList pairedDevicesArayList = new ArrayList();

        //Add the Device name and address to the arraylist
        for(BluetoothDevice bluetoothDevice : pairedDevices){
            pairedDevicesArayList.add(bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress());
        }

        final ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, pairedDevicesArayList);

        pairedDeviceslistView.setAdapter(arrayAdapter);

        //Get address and toast from ListView
        pairedDeviceslistView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                state = SEND;
                if (acceptThread != null) {
                    acceptThread.cancel();
                    acceptThread = null;
                }
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);
                Toast.makeText(getApplicationContext(), address, Toast.LENGTH_LONG).show();

                BluetoothDevice device = BA.getRemoteDevice(address);
                // Attempt to connect to the device
                connectThread = new ConnectThread(device);
                connectThread.start();
                int bond = device.getBondState();
                Toast.makeText(getApplicationContext(), Integer.toString(bond), Toast.LENGTH_LONG).show();
                //networkAdapter.writePlay();

                Message listener = null;
                networkAdapter.setMessageListener(listener);
            }
        });

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        send = (Button) findViewById(R.id.send);
        listener = new Message();
        if(BA.isEnabled()){
            Toast.makeText(getApplicationContext(), "Bluetooth is on", Toast.LENGTH_LONG).show();
        } else {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(i);

            if(BA.isEnabled()){
                Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_LONG).show();

            }
        }
        state = RECIEVE;
        acceptThread = new AcceptThread();
        acceptThread.start();
        state = RECIEVE;

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    public void sendMessage(View view){
        networkAdapter.writePlay();
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            BA.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
                Log.i("Connect Thread", "Connected");

            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }
//            // Do work to manage the connection (in a separate thread)
//            manageConnectedSocket(mmSocket);
            networkAdapter = new NetworkAdapter(mmSocket);
            networkAdapter.setMessageListener(listener);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }


    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = BA.listenUsingRfcommWithServiceRecord("Secure", MY_UUID);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                Log.i("Waiting ", "For Connetion");
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    Log.i("Socket ", "Found");
                    // Do work to manage the connection (in a separate thread)
                    BluetoothDevice device = socket.getRemoteDevice();
                    networkAdapter = new NetworkAdapter(socket);
                    networkAdapter.setMessageListener(listener);
                    Log.i("RemoteDevice", device.getName());
                    while(state==RECIEVE){
                        Log.i("Main", "Recieve");
                        networkAdapter.receiveMessages();
                    }

                    try{
                        mmServerSocket.close();
                    }catch (IOException e){

                    }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) { }
        }
    }


}
