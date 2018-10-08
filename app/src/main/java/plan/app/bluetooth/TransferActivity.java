package plan.app.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TransferActivity extends AppCompatActivity {

    private boolean isServer;
    private Handler handler;
    private BluetoothSocket bluetoothSocket;
    private GameThread game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        isServer = ConnectActivity.isServer;
        bluetoothSocket = ConnectActivity.bluetoothSocket;
        handler = new Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message message)
            {
                if(message.what == MessageConstants.MESSAGE_READ)
                {
                    // reagoi datasyötteeseen. palvelin laskee tiedoilla pelin uuden tilan
                    // ja asiakas päivittää tiedoilla pelin tilan.
                }
                else if(message.what == MessageConstants.MESSAGE_WRITE)
                {
                    // jos tarvitsee, jaa viesti ui lankaan
                }
                else if(message.what == MessageConstants.MESSAGE_TOAST)
                {
                    Toast.makeText(getApplicationContext(), message.getData().getString("toast"), Toast.LENGTH_LONG).show();
                }
            }
        };

        try
        {
            this.getSupportActionBar().hide();
        }
        catch (NullPointerException e){}

        game = new GameThread(this, TransferActivity.this);
        setContentView(game.cv);
        game.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        game.touchEvent(event);
        return super.onTouchEvent(event);
    }

    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;
    }

    private class ConnectedThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                tmpIn = socket.getInputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            try
            {
                tmpOut = socket.getOutputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes;

            while (true) {
                try
                {
                    numBytes = mmInStream.read(mmBuffer);
                    Message readMsg = handler.obtainMessage(MessageConstants.MESSAGE_READ, numBytes, -1, mmBuffer);
                    readMsg.sendToTarget();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try
            {
                mmOutStream.write(bytes);

                Message writtenMsg = handler.obtainMessage(MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();
            }
            catch (IOException e)
            {
                e.printStackTrace();

                Message writeErrorMsg = handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast", "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                handler.sendMessage(writeErrorMsg);
            }
        }

        public void cancel()
        {
            try
            {
                mmSocket.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
