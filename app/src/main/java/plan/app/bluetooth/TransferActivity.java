package plan.app.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TransferActivity extends AppCompatActivity {

    private boolean isServer;
    private BluetoothSocket bluetoothSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        isServer = MainActivity.isServer;
        bluetoothSocket = MainActivity.bluetoothSocket;
    }

    private interface MessageConstants
    {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 0;
    }

    private class ServerThread extends Thread
    {
        private InputStream inputStream;
        private OutputStream outputStream;
        private byte[] mmBuffer;
        private Handler handler;

        public ServerThread(BluetoothSocket socket)
        {
            try
            {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
            handler = new Handler(Looper.getMainLooper());
        }

        public void run()
        {
            mmBuffer = new byte[1024];
            int numBytes;

            while (true) {
                try
                {
                    numBytes = inputStream.read(mmBuffer);
                    Message readMsg = handler.obtainMessage(MessageConstants.MESSAGE_READ, numBytes, -1, mmBuffer);
                    readMsg.sendToTarget();
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                }
            }

        }
    }

    private class ClientThread extends Thread
    {
        public ClientThread()
        {

        }

        public void run()
        {

        }
    }
}
