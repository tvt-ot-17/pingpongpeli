package plan.app.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static int REQUEST_ENABLE_BT = 0;
    private static int REQUEST_ASK_COARSE_LOCATION = 1;
    private static int REQUEST_ENABLE_DISCOVERABILITY = 2;

    private final int discoverabilityTime = 60;
    private final String SERVICE_NAME = "Ping Pong";
    private final String MY_UUID = "41ba0542-681a-403c-9f08-5b572e15ff47";
    private final String TAG = "PingPongTag";

    private Button enableButton;
    private Button connectButton;

    private ListView devicesListView;
    private ArrayAdapter<String> arrayAdapter;
    private String selectedDevice = null;

    private BluetoothAdapter bluetoothAdapter;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                arrayAdapter.add(device.getName() + "\n" + device.getAddress());
                arrayAdapter.notifyDataSetChanged();
            }
        }
    };

    public static BluetoothSocket bluetoothSocket = null;
    public static boolean isServer;

    private ServerThread serverThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null)
        {
            System.exit(0);
        }

        checkPermissions();
        enableBluetooth();

        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(broadcastReceiver, intentFilter);

        if(bluetoothAdapter.isEnabled())
        {
            enableDiscoverability();
            bluetoothAdapter.startDiscovery();

            serverThread = new ServerThread();
            serverThread.start();
        }

        devicesListView = (ListView) findViewById(R.id.devicesListView);
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                selectedDevice = devicesListView.getItemAtPosition(i).toString() ;
                Toast.makeText(getApplicationContext(), "Laite " + selectedDevice + " valittu.", Toast.LENGTH_SHORT).show();
            }
        });

        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        devicesListView.setAdapter(arrayAdapter);

        listBondedDevices();

        enableButton = (Button)findViewById(R.id.enableButton);
        connectButton = (Button)findViewById(R.id.connectButton);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
        if(serverThread != null)
        {
            serverThread.interrupt();
            serverThread.cancel();
            serverThread = null;
        }
    }

    protected void enableBluetooth()
    {
        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    protected void checkPermissions()
    {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED;
        if(!hasPermission)
        {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_ASK_COARSE_LOCATION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode == REQUEST_ENABLE_BT)
        {
            if(resultCode == RESULT_OK)
            {
                enableDiscoverability();
                bluetoothAdapter.startDiscovery();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String [] permissions, int [] grantResults)
    {
        if(requestCode == REQUEST_ASK_COARSE_LOCATION)
        {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                recreate();
            }
            else
            {
                Toast.makeText(this, "Access Coarse Location -lupaa ei myönnetty.", Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void enableDiscoverability()
    {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverabilityTime);
        startActivityForResult(discoverableIntent, REQUEST_ENABLE_DISCOVERABILITY);
    }

    private void listBondedDevices()
    {
        for(BluetoothDevice device : bluetoothAdapter.getBondedDevices())
        {
            arrayAdapter.add(device.getName() + "\n" + device.getAddress());
        }
        arrayAdapter.notifyDataSetChanged();
    }

    public synchronized void connected(BluetoothSocket socket, boolean isServer)
    {
        bluetoothSocket = socket;
        this.isServer = isServer;

        Intent intent = new Intent(MainActivity.this, TransferActivity.class);
        startActivity(intent);
    }

    public void onEnableClick(View view)
    {
        if(!bluetoothAdapter.isEnabled())
        {
            enableBluetooth();
        }
    }

    public void onConnectClick(View view)
    {
        if(selectedDevice == null)
        {
            Toast.makeText(this, "Select device from the list.", Toast.LENGTH_SHORT).show();
        }
        else
        {
            if(serverThread != null)
            {
                serverThread.interrupt();
                serverThread.cancel();
                serverThread = null;
            }

            bluetoothAdapter.cancelDiscovery();

            String macAddress = selectedDevice.split("\n")[1];

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            new ClientThread(device).start();
        }
    }

    private class ServerThread extends Thread
    {
        private final BluetoothServerSocket mmServerSocket;

        public ServerThread()
        {
            BluetoothServerSocket tmp = null;
            try
            {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, UUID.fromString(MY_UUID));
            }
            catch (IOException e)
            {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }


        public void run()
        {
            BluetoothSocket socket = null;
            while (true)
            {
                try
                {
                    socket = mmServerSocket.accept();
                }
                catch (IOException e)
                {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null)
                {
                    connected(socket, true);
                    try
                    {
                        mmServerSocket.close();
                    }
                    catch(IOException e)
                    {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try
            {
                mmServerSocket.close();
            }
            catch (IOException e)
            {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }


    private class ClientThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ClientThread(BluetoothDevice device)
        {
            BluetoothSocket tmp = null;
            mmDevice = device;

            try
            {
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
            }
            catch (IOException e)
            {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run()
        {
            bluetoothAdapter.cancelDiscovery();

            try
            {
                mmSocket.connect();
            }
            catch (IOException connectException)
            {
                try
                {
                    mmSocket.close();
                }
                catch (IOException closeException)
                {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }
            connected(mmSocket, false);
        }

        public void cancel() {
            try
            {
                mmSocket.close();
            }
            catch (IOException e)
            {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }
}
