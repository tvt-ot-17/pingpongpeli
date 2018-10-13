package plan.app.bluetooth;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;

public class GameActivity extends AppCompatActivity {
    private GameThread game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //game = new GameThread(this, GameActivity.this);
        //setContentView(game.cv);
        //game.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        game.touchEvent(event);
        return super.onTouchEvent(event);
    }
}
