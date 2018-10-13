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

    private GameThread game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        /*
        game = new GameThread(this, TransferActivity.this);
        setContentView(game.cv);
        game.start();
        */
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        game.touchEvent(event);
        return super.onTouchEvent(event);
    }

}
