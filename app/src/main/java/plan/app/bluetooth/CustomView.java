package plan.app.bluetooth;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public class CustomView extends View {
    private Rect bat;
    private Rect batOpponent;
    private Rect ball;

    private Paint paintBat;
    private Paint paintBatOpponent;
    private Paint paintBall;
    private Paint paintText;
    private Paint paintDebug;

    private GameThread game;
    private Activity activity;

    boolean isScoreTextEnabled = false;
    int scoreTextCounter;

    public CustomView(Context context, GameThread game, Activity activity) {
        super(context);
        this.game = game;
        this.activity = activity;

        // rects to be drawn
        bat = new Rect(
                game.getBatX(),
                game.getBatY(),
                game.getBatX() + game.getBatWidth(),
                game.getBatY() + game.getBatHeight());

        batOpponent = new Rect(
                game.getBatOppX(),
                game.getBatOppY(),
                game.getBatOppX() + game.getBatWidth(),
                game.getBatOppY() + game.getBatHeight());

        ball = new Rect(
                game.getBallX(),
                game.getBallY(),
                game.getBallX() + game.getBallSide(),
                game.getBallY() + game.getBallSide());

        // and paints for painting
        paintBat = new Paint();
        paintBat.setColor(Color.WHITE);

        paintBatOpponent = new Paint();
        paintBatOpponent.setColor(Color.GRAY);

        paintBall = new Paint();
        paintBall.setColor(Color.GREEN);

        paintText = new Paint();
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(150);

        paintDebug = new Paint();
        paintDebug.setColor(Color.WHITE);
        paintDebug.setTextSize(50);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // background color
        canvas.drawColor(Color.BLUE);

        // actual drawing of the game
        canvas.drawRect(bat, paintBat);
        canvas.drawRect(batOpponent, paintBatOpponent);
        canvas.drawRect(ball, paintBall);

        if (isScoreTextEnabled) {
            String gameScoreText = game.getScore() + " - " + game.getScoreOpp();
            float width = paintText.measureText(gameScoreText);

            canvas.drawText(gameScoreText, game.getScoreX() - width / 2, game.getScoreY(), paintText);

            scoreTextCounter--; // draw call counter
            if (scoreTextCounter < 0) {
                isScoreTextEnabled = false;
            }
        }

        // debug message draw
        if (game.getDebug_show()) {
            for (int i = 1; i <= 4; i++) {
                canvas.drawText(game.getDebug_msg(i), 25, i * 50, paintDebug);
            }
        }
    }

    public void update() {
        // update rects to match gamestate
        bat.set(
                game.getBatX(),
                game.getBatY(),
                game.getBatX() + game.getBatWidth(),
                game.getBatY() + game.getBatHeight());

        batOpponent.set(
                game.getBatOppX(),
                game.getBatOppY(),
                game.getBatOppX() + game.getBatWidth(),
                game.getBatOppY() + game.getBatHeight());

        ball.set(game.getBallX(),
                game.getBallY(),
                game.getBallX() + game.getBallSide(),
                game.getBallY() + game.getBallSide());
    }

    public void showScore(int drawCalls) {
        isScoreTextEnabled = true;
        scoreTextCounter = drawCalls;
    }

    public void invalidateView() {
        activity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                invalidate();
            }
        });
    }
}
