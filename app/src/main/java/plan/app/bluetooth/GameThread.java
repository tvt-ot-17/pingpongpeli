package plan.app.bluetooth;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.util.Random;

enum ControlScheme { HALF, FOLLOW }
enum ScoringScheme { POINT, SPEED }

public class GameThread extends Thread {
    public CustomView cv;

    private int draw = 0;
    private int wiggle = 0;
    private Random random = new Random();

    private int targetFrameRate;
    private int targetMillis;
    private int targetDrawFrameCount;

    private int ballSpeedX;
    private int ballSpeedY;
    private int ballSpeedDefault;
    private int ballSpeedIncrease;
    private int ballSpeedMax;
    private int ballWiggleInterval;
    private int ballWiggleMax;

    private float gameHeight;
    private float gameWidth;
    private int screenWidth;
    private int screenHeight;
    private float scaleX;
    private float scaleY;

    private boolean isTouchDown = false;
    private ControlScheme controlScheme;
    private ScoringScheme scoringScheme;

    private float batMove;
    private float batX;
    private float batY;
    private float batOppX;
    private float batOppY;
    private float ballX;
    private float ballY;

    private float batWidth;
    private float batHeight;
    private float ballSide;
    private float ballDefaultX;
    private float ballDefaultY;

    private int score;
    private int scoreOpp;
    private float scoreX;
    private float scoreY;

    private float touchX;
    private float touchY;

    private boolean DEBUG_automove_player;
    private boolean DEBUG_automove_opponent;
    private final int PLAYER = 0;   // TODO: do this type safely
    private final int OPPONENT = 1; // TODO: and this

    GameThread(Context context, Activity activity) {
        Log.d("GAMETHREAD", "gameThread init");

        // get screen resolution
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        int statusbarHeight = getStatusBarHeight(context);

        // frame rate is only a target, not a guarantee!
        targetFrameRate = 180;          // internal update
        targetMillis = 1000 / targetFrameRate;
        targetDrawFrameCount = 2;       // actual drawing fps is target divided by this (180 / 2 = 90 fps)
        gameHeight = 1280;              // internal resolution
        gameWidth = 720;                //
        scaleX = screenWidth / gameWidth;
        scaleY = screenHeight / gameHeight;

        // starting parameters
        batMove = 9;                    // bat moving speed
        batWidth = 150;                 //
        batHeight = 15;                 //
        ballSide = 15;                  // ball width and height
        ballSpeedDefault = 4;           // ball speed at spawn
        ballSpeedIncrease = 1;          // for each bat hit
        ballSpeedMax = 25;              // max speed. increase frame rate _when_ problems occur.
        score = 0;                      //
        scoreOpp = 0;                   //
        ballWiggleInterval = 720;       // wiggle interval uses thread frame rate
        ballWiggleMax = 1;              // max random

        // internal starting positions
        batX = gameWidth / 2 - batWidth / 2;
        batY = gameHeight - batHeight - Math.round(statusbarHeight / scaleY) - 20; // magic margin
        batOppX = gameWidth / 2 - batWidth / 2;
        batOppY = batHeight + 20; // magic margin
        ballDefaultX = gameWidth / 2;
        ballDefaultY = gameHeight / 3;
        scoreX = gameWidth / 2;
        scoreY = gameHeight / 3;

        // HALF     = touch the left side of the screen to go left and vice versa
        // FOLLOW   = bat follows touch
        controlScheme = ControlScheme.HALF;

        // POINT    = one point per death
        // SPEED    = ball's current speed per death
        scoringScheme = ScoringScheme.SPEED;

        DEBUG_automove_player = false;
        DEBUG_automove_opponent = true;

        respawnBall();
        this.cv = new CustomView(context, this, activity);

        // debug prints
        Log.d("GAMETHREAD", "targetFrameRate/millis:" + targetFrameRate + " / " + targetMillis);
        Log.d("GAMETHREAD", "targetDrawFrameCount:" + targetDrawFrameCount);
        Log.d("GAMETHREAD", "targetDrawFPS:" + targetFrameRate / targetDrawFrameCount);
        Log.d("GAMETHREAD", "screen:" + screenHeight + "x" + screenWidth);
        Log.d("GAMETHREAD", "statusbar height:" + statusbarHeight);

        Log.d("GAMETHREAD","scaleX:" + scaleX);
        Log.d("GAMETHREAD","scaleY:" + scaleY);
        Log.d("GAMETHREAD","batMove:" + batMove);
        Log.d("GAMETHREAD","batWidth:" + batWidth);
        Log.d("GAMETHREAD","batHeight:" + batHeight);
        Log.d("GAMETHREAD","ballSide:" + ballSide);
    }

    public void run() {
        Log.d("GAMETHREAD", "thread running");

        while(true) {
            try {
                Thread.sleep(targetMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            ballWiggle();
            ballMove();
            batMove();
            collisionCheck();
            draw();
        }
    }

    private void ballWiggle() {
        // TODO: this is shit, do it again.
        wiggle++;

        if (wiggle >= ballWiggleInterval) {
            int n;

            n = random.nextInt(ballWiggleMax);
            ballSpeedX += n;
            Log.d("WIGGLE", "wiggled ball speed x: " + n);

            n = random.nextInt(ballWiggleMax);
            ballSpeedY += n;
            Log.d("WIGGLE", "wiggled ball speed y: " + n);

            wiggle = 0;
        }
    }

    private void ballMove() {
        ballX += ballSpeedX;
        ballY += ballSpeedY;
    }

    private void draw() {
        draw++;

        if (draw >= targetDrawFrameCount) {
            // update rectangles to match current game state
            cv.update();

            // invoke onDraw in CustomView
            cv.invalidateView();

            // set draw counter to zero
            draw = 0;
        }
    }

    private void score(int side) {
        // 0 = player
        // 1 = opponent

        switch (scoringScheme) {
            case POINT:
                if (side == PLAYER) {
                    score++;
                } else if (side == OPPONENT) {
                    scoreOpp++;
                }

                break;

            case SPEED:
                int speedScore = Math.abs(ballSpeedX) - ballSpeedDefault + 1;

                if (side == PLAYER) {
                    score += speedScore;
                } else if (side == OPPONENT) {
                    scoreOpp += speedScore;
                }

                Log.d("SCORE", "speedScore: " + speedScore);
                break;

            default: Log.d("SCORE", "scoringScheme defaults");
        }

        cv.showScore(180);  // show score for 2 seconds (drawCalls / frame rate)
        respawnBall();

        Log.d("SCORE", "score: " + score);
        Log.d("SCORE", "scoreOpp: " + scoreOpp);
    }

    private void collisionCheck() {
        // opponent scores
        if (ballY > gameHeight - ballSide) {
            Log.d("COLLISION", "death bottom");

            score(OPPONENT);
        }

        // player scores
        if (ballY < 0) {
            Log.d("COLLISION", "death top");

            score(PLAYER);
        }

        // left side collision
        if (ballX < 0) {
            Log.d("COLLISION", "left side");

            ballSpeedX *= -1;
            ballX = 0; // prevents double collision
        }

        // right side collision
        if (ballX > gameWidth - ballSide) {
            Log.d("COLLISION", "right side");

            ballSpeedX *= -1;
            ballX = gameWidth - ballSide; // same
        }

        // player bat collision check
        if (ballY + ballSide > batY && ballY < batY + batHeight) {
            if (ballX + ballSide > batX && ballX < batX + batWidth) {
                Log.d("COLLISION", "bat");

                ballSpeedY *= -1;
                increaseBallSpeed();

                // prevents double collision
                ballY = batY - batHeight;
            }
        }

        // opponent bat collision check
        if (ballY < batOppY + batHeight && ballY > batOppY) {
            if (ballX + ballSide > batOppX && ballX < batOppX + batWidth) {
                Log.d("COLLISION", "opp bat");

                ballSpeedY *= -1;
                increaseBallSpeed();

                // same
                ballY = batOppY + batHeight;
            }
        }
    }

    private void batMove() {
        // auto move bat for debugging
        if (DEBUG_automove_player) {
            batX = ballX - batWidth / 2;
        }

        if (DEBUG_automove_opponent) {
            batOppX = ballX - batWidth / 2;
        }

        // actual moving here
        if (isTouchDown) {
            // -1 left
            // +1 right
            int batMoveDirection = 0;

            switch (controlScheme) {
                case HALF:
                    if (touchX < Math.floor(screenWidth / 2)) {
                        batMoveDirection = -1;
                    } else {
                        batMoveDirection = 1;
                    }

                    break;
                case FOLLOW:
                    float followTarget = touchX - batWidth / 2;
                    float follower = batX + batWidth / 2;

                    if (followTarget < follower) {
                        batMoveDirection = -1;
                    } else {
                        batMoveDirection = 1;
                    }

                    break;
                default: Log.d("BATMOVE", "batMove() scheme defaults");
            }

            batX += batMove * batMoveDirection;

            // a non-sticky hack for bat bounds check
            if (batX < 0) {
                batX = 0;
            }

            if (batX > gameWidth - batWidth) {
                batX = gameWidth - batWidth;
            }
        }
    }

    private void increaseBallSpeed() {
        if (Math.abs(ballSpeedX) < ballSpeedMax) {
            if (ballSpeedX < 0) {
                ballSpeedX -= ballSpeedIncrease;
            } else {
                ballSpeedX += ballSpeedIncrease;
            }
        }

        if (Math.abs(ballSpeedY) < ballSpeedMax) {
            if (ballSpeedY < 0) {
                ballSpeedY -= ballSpeedIncrease;
            } else {
                ballSpeedY += ballSpeedIncrease;
            }
        }

        Log.d("INC_BALL_SPD", "ballSpeedX: " + ballSpeedX);
        Log.d("INC_BALL_SPD", "ballSpeedY: " + ballSpeedY);
    }

    private void respawnBall() {
        ballX = ballDefaultX;
        ballY = ballDefaultY;
        ballSpeedY = ballSpeedDefault;
        ballSpeedX = ballSpeedDefault;

        Log.d("RESPAWN","ball");
    }

    public void touchEvent(MotionEvent event) {
        touchX = event.getX();
        touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d("TOUCH_EVENT" , "touch down");
                isTouchDown = true;
                break;

            case MotionEvent.ACTION_UP:
                isTouchDown = false;
                Log.d("TOUCH_EVENT" , "touch up");
                break;
        }

        Log.d("TOUCH_EVENT", "xy: " + touchX + ", " + touchY);
    }

    public int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");

        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }


    // getters for CustomView
    public int getBatX() {
        return Math.round(batX * scaleX);
    }

    public int getBatY() {
        return Math.round(batY * scaleY);
    }

    public int getBatOppX() {
        return Math.round(batOppX * scaleX);
    }

    public int getBatOppY() {
        return Math.round(batOppY * scaleY);
    }

    public int getBatWidth() {
        return Math.round(batWidth * scaleX);
    }

    public int getBatHeight() {
        return Math.round(batHeight * scaleY);
    }

    public int getBallX() {
        return Math.round(ballX * scaleX);
    }

    public int getBallY() {
        return Math.round(ballY * scaleY);
    }

    public int getBallSide() {
        return Math.round(ballSide * scaleX);
    }

    public float getScoreX() {
        return Math.round(scoreX * scaleX);
    }

    public float getScoreY() {
        return Math.round(scoreY * scaleY);
    }

    public int getScore() {
        return score;
    }

    public int getScoreOpp() {
        return scoreOpp;
    }
}
