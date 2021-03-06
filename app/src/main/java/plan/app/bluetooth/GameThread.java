package plan.app.bluetooth;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.util.Random;

enum ScoringScheme { POINT, SPEED }

public class GameThread extends Thread {
    public CustomView cv;
    private ConnectActivity ca;

    // counters
    private int draw = 0;
    private int wiggle = 0;
    private int sync = 0;
    private boolean btSyncCounter = false;

    private Random random = new Random();

    private int targetFrameRate;
    private int targetMillis;
    private int targetDrawFrameCount;
    private int btSyncWaitCount;

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
    private ScoringScheme scoringScheme;

    private float batMove;
    private float batX;
    private float batY;
    private float batDiffBufferX;
    private float batOppX;
    private float batOppY;
    private float batDiffSmooth;
    private float batDiffThreshold;

    private float ballX;
    private float ballY;
    private float ballDiffBufferX;
    private float ballDiffBufferY;
    private float ballDiffSmooth;
    private float ballDiffThreshold;

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
    private boolean DEBUG_show_debug;
    private String DEBUG_sync_ball_buffer = "";
    private String DEBUG_sync_ball_diff = "";
    private String DEBUG_sync_bat_buffer = "";
    private String DEBUG_sync_bat_diff = "";

    private final int PLAYER = 0;   // TODO: do this type safely
    private final int OPPONENT = 1; // TODO: and this

    private final boolean isServer;

    // client specific variables
    private boolean isClientTouchDown = false;
    private int batClientMoveDirection;

    GameThread(Context context, Activity activity, ConnectActivity ca, boolean isServer) {
        Log.d("GAMETHREAD", "gameThread init");

        this.ca = ca;
        this.isServer = isServer;

        // get screen resolution
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm != null ? wm.getDefaultDisplay() : null;

        if (wm != null) {
            Point size = new Point();
            display.getSize(size);
            screenWidth = size.x;
            screenHeight = size.y;
        } else {
            Log.e("GAMETHREAD", "cannot get default display, assuming fhd");
            screenWidth = 1080;
            screenHeight = 1920;
        }

        int statusbarHeight = getStatusBarHeight(context);

        // frame rate is only a target, not a guarantee!
        targetFrameRate = 60;          // internal update
        targetMillis = 1000 / targetFrameRate;
        targetDrawFrameCount = 1;       // actual drawing fps is target divided by this (180 / 2 = 90 fps)
        btSyncWaitCount = 7;            // resend bt sync after this many frames have passed
        gameHeight = 1920;              // internal resolution
        gameWidth = 1080;               //
        scaleX = screenWidth / gameWidth;
        scaleY = (screenHeight - statusbarHeight) / gameHeight;

        // starting parameters
        batMove = 20;                   // bat moving speed
        batWidth = 200;                 //
        batHeight = 15;                 //
        ballSide = 15;                  // ball width and height
        ballSpeedDefault = 3;           // ball speed at spawn
        ballSpeedIncrease = 1;          // for each bat hit
        ballSpeedMax = 10;              // max speed. increase frame rate _when_ problems occur.
        score = 0;                      //
        scoreOpp = 0;                   //
        ballWiggleInterval = 720;       // wiggle interval uses thread frame rate
        ballWiggleMax = 1;              // max random

        // internal starting positions
        batX = gameWidth / 2 - batWidth / 2;
        batY = gameHeight - batHeight - 75;     // magic margin
        batOppX = gameWidth / 2 - batWidth / 2;
        batOppY = batHeight + 75;               // magic margin
        ballDefaultX = gameWidth / 2;
        ballDefaultY = gameHeight / 3;
        scoreX = gameWidth / 2;
        scoreY = gameHeight / 3;

        // client qol settings
        ballDiffSmooth = 1;
        ballDiffThreshold = 50;
        batDiffSmooth = 20;
        batDiffThreshold = 300;

        // POINT    = one point per death
        // SPEED    = speed equals points
        scoringScheme = ScoringScheme.SPEED;

        DEBUG_automove_player = false;
        DEBUG_automove_opponent = false;
        DEBUG_show_debug = true;

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

        btUpdateBatPosition();
        respawnBall();

        //noinspection InfiniteLoopStatement
        while(true) {
            try {
                Thread.sleep(targetMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            ballMove();
            collisionCheck();
            btSync();

            if (isServer) {
                //ballWiggle();
                batMove();
            }

            draw();
        }
    }

    //region Bluetooth

    // server send functions

    // sync speed and position at every collision
    // client moves the ball
    private void btUpdateSync(boolean counter) {
        String syncmsg = "s:";
        syncmsg += ballX + ":";
        syncmsg += ballY + ":";
        syncmsg += ballSpeedX + ":";
        syncmsg += ballSpeedY + ":";

        ca.sendMessage(syncmsg);

        // server begins to wait for sync confirmation
        this.sync = 0;
        btSyncCounter = counter;
    }

    private void btUpdateScore() {
        ca.sendMessage("score:" + score + ";" + scoreOpp);
    }

    private void btUpdateBatPosition() {
        ca.sendMessage("bat:" + batX + ";" + batY + ";" + batOppX + ";" + batOppY);
    }

    // client send functions
    private void btClientUpdateTouch(String isClientTouchDown, String direction) {
        ca.sendMessage("touch:" + isClientTouchDown + ";" + direction);
    }

    private void btClientConfirmSync() {
        Log.d("BT_CLIENT_SEND", "send sync confirm");
        ca.sendMessage("sync:1");
    }

    // receive functions
    // ConnectActivity.handler calls this
    void btReceiveMessage(String msg) {
        // Log.d("BT_RECEIVE", msg);
        // expected msg: type:variable

        String[] parts = msg.split(":");

        if (parts.length < 2) {
            Log.e("BT_RECEIVE", "bt received too short message");
        } else {
            switch (parts[0]) {
                case "s":
                    btClientSetSync(parts);
                    break;
                case "bat":
                    btClientSetBatPosition(parts[1]);
                    break;
                case "score":
                    btClientSetScore(parts[1]);
                    break;
                case "touch": // server receive
                    btServerSetTouch(parts[1]);
                    break;
                case "sync": // server receive
                    btServerSyncConfirm();
                    break;
                default:
                    Log.e("BT_RECEIVE", "parts[0] switch defaults: " + parts[0]);
            }
        }
    }

    private void btClientSetSync(String[] data) {
        if (data.length == 5) {
            try {
                float tmp_ballX = Float.parseFloat(data[1]);
                float tmp_ballY = Float.parseFloat(data[2]);
                int tmp_bsX = Integer.parseInt(data[3]);
                int tmp_bsY = Integer.parseInt(data[4]);

                float diffX = gameWidth - tmp_ballX - ballX;
                float diffY = gameHeight - tmp_ballY - ballY;

                ballDiffBufferX = diffX;
                ballDiffBufferY = diffY;

                ballSpeedX = tmp_bsX * -1;
                ballSpeedY = tmp_bsY * -1;

                btClientConfirmSync();

                if (DEBUG_show_debug) {
                    DEBUG_sync_ball_diff = "ball diff xy: " + diffX + " " + diffY;
                }
            } catch (Exception ignored) {

            }
        } else {
            Log.e("BT_SYNCPACKET", "wrong packet length: " + data.length);
        }
    }

    private void btClientSetBatPosition(String msg) {
        // expected msg: batx;baty;oppx;oppy
        //Log.d("BT_RECEIVE", msg);

        String[] parts = msg.split(";");

        // reversed
        try {
            float tmp_batX = Float.parseFloat(parts[2]);
            float tmp_batY = Float.parseFloat(parts[3]);
            float tmp_batOppX = Float.parseFloat(parts[0]);
            float tmp_batOppY = Float.parseFloat(parts[1]);

            // reversed
            batOppX = gameWidth - tmp_batOppX - batWidth;
            batOppY = gameHeight - tmp_batOppY;

            float diffX = gameWidth - tmp_batX - batWidth - batX;

            batDiffBufferX = diffX;
            batY = gameHeight - tmp_batY;

            if (DEBUG_show_debug) {
                DEBUG_sync_bat_diff = "bat diff x: " + diffX;
            }
        } catch (Exception ignored) {

        }
    }

    private void btClientSetScore(String msg) {
        // expected msg: score;oppscore
        //Log.d("BT_RECEIVE", "score: " + msg);

        String[] parts = msg.split(";");

        // reversed
        try {
            scoreOpp = Integer.parseInt(parts[0]);
            score = Integer.parseInt(parts[1]);

            cv.showScore(180);
        } catch (Exception ignored) {

        }
    }

    // server receive functions
    private void btServerSetTouch(String msg) {
        // expected msg: boolean;direction (true,false;left,right)

        Log.d("BT_RECEIVE", "setTouch: " + msg);
        String[] parts = msg.split(";");

        try {
            if (parts.length >= 1) {
                if (parts[0].equals("true")) {
                    isClientTouchDown = true;
                } else {
                    isClientTouchDown = false;
                }

                // reversed
                if (parts[1].equals("left")) {
                    batClientMoveDirection = 1;
                } else {
                    batClientMoveDirection = -1;
                }
            }
        } catch (Exception ignored) {

        }
    }

    private void btServerSyncConfirm() {
        Log.d("BT_RECEIVE", "sync confirmed");

        // reset sync counter
        btSyncCounter = false;
        sync = 0;
    }

    //endregion

    //region Thread functions
    private void ballWiggle() {
        // TODO: this is shit, do it again.
        // anyways, this stays disabled for now
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
            btUpdateSync(true);
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

    private void btSync() {
        if (isServer) {
            // this runs when the server waits for sync confirmation
            if (btSyncCounter) {
                sync++;

                if (sync > btSyncWaitCount) {
                    btUpdateSync(true);
                    // because bt is absolute garbage, this shit happens all the fucking time.
                    // have fun.
                    //Log.e("BT_SYNC", "sync counter reached target, sending new sync");
                }
            }
        } else {

            if (DEBUG_show_debug) {
                DEBUG_sync_ball_buffer = "ball diff buffer xy: " + ballDiffBufferX + " " + ballDiffBufferY;
                DEBUG_sync_bat_buffer = "bat diff buffer x: " + batDiffBufferX;
            }

            // ball position sync smoothing
            if (Math.abs(ballDiffBufferX) < ballDiffThreshold || Math.abs(ballDiffBufferY) < ballDiffThreshold ) {
                if (ballDiffBufferX > 0) {
                    ballDiffBufferX -= ballDiffSmooth;
                    ballX += ballDiffSmooth;
                } else {
                    ballDiffBufferX += ballDiffSmooth;
                    ballX -= ballDiffSmooth;
                }

                if (ballDiffBufferY > 0) {
                    ballDiffBufferY -= ballDiffSmooth;
                    ballY += ballDiffSmooth;
                } else {
                    ballDiffBufferY += ballDiffSmooth;
                    ballY -= ballDiffSmooth;
                }
            } else {
                // skip smoothing when over threshold
                Log.e("SMOOTH", "ball smoothing skipped!");

                ballX += ballDiffBufferX;
                ballDiffBufferX = 0;

                ballY += ballDiffBufferY;
                ballDiffBufferY = 0;
            }

            // bat position sync smoothing
            if (Math.abs(batDiffBufferX) < batDiffThreshold) {
                if (batDiffBufferX > 1) {
                    if (batDiffBufferX >= batDiffSmooth) {
                        batDiffBufferX -= batDiffSmooth;
                        batX += batDiffSmooth;
                    }
                }

                if (batDiffBufferX < -1) {
                    if (batDiffBufferX <= batDiffSmooth) {
                        batDiffBufferX += batDiffSmooth;
                        batX -= batDiffSmooth;
                    }
                }
            } else {
                // skip smoothing
                Log.e("SMOOTH", "bat smoothing skipped!");

                batX += batDiffBufferX;
                batDiffBufferX = 0;
            }
        }
    }

    private void collisionCheck() {
        // opponent scores
        if (ballY > gameHeight - ballSide) {
            Log.d("COLLISION", "death bottom");

            if (isServer) {
                score(OPPONENT);
            }

            respawnBall();
            return;
        }

        // player scores
        if (ballY < 0) {
            Log.d("COLLISION", "death top");

            if (isServer) {
                score(PLAYER);
            }

            respawnBall();
            return;
        }

        // left side collision
        if (ballX < 0) {
            Log.d("COLLISION", "left side");

            ballSpeedX *= -1;
            ballX = 0; // prevents double collision

            if (isServer) {
                btUpdateSync(true);
            }

            return;
        }

        // right side collision
        if (ballX > gameWidth - ballSide) {
            Log.d("COLLISION", "right side");

            ballSpeedX *= -1;
            ballX = gameWidth - ballSide; // same

            if (isServer) {
                btUpdateSync(true);
            }

            return;
        }

        // player bat collision check
        if (ballY + ballSide > batY && ballY < batY + batHeight) {
            if (ballX + ballSide > batX && ballX < batX + batWidth) {
                Log.d("COLLISION", "bat");

                ballSpeedY *= -1;
                increaseBallSpeed();

                // prevents double collision
                ballY = batY - batHeight;

                if (isServer) {
                    btUpdateSync(true);
                }

                return;
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

                if (isServer) {
                    btUpdateSync(true);
                }
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

        // update clients touch
        if (isClientTouchDown) {
            batOppX += batMove * batClientMoveDirection;

            // a non-sticky hack for bat bounds check
            if (batOppX < 0) {
                batOppX = 0;
            }

            if (batOppX > gameWidth - batWidth) {
                batOppX = gameWidth - batWidth;
            }
        }

        // actual moving here
        if (isTouchDown) {
            // -1 left
            // +1 right
            int batMoveDirection = 0;

            if (touchX < Math.floor(screenWidth / 2)) {
                batMoveDirection = -1;
            } else {
                batMoveDirection = 1;
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

        if (isServer || isClientTouchDown) {
            btUpdateBatPosition();
        }
    }
    //endregion

    //region Utility
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

        Log.d("SCORE", "score: " + score);
        Log.d("SCORE", "scoreOpp: " + scoreOpp);

        btUpdateScore();
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
        if (isServer) {
            ballSpeedY = ballSpeedDefault;
            ballSpeedX = ballSpeedDefault;
            ballX = ballDefaultX;
            ballY = ballDefaultY;

            btUpdateSync(true);
        } else {
            ballX = gameWidth - ballDefaultX;
            ballY = gameHeight - ballDefaultY;
            ballSpeedY = -ballSpeedDefault;
            ballSpeedX = -ballSpeedDefault;
        }

        ballDiffBufferX = 0;
        ballDiffBufferY = 0;


        Log.d("RESPAWN","ball");
    }

    void touchEvent(MotionEvent event) {
        touchX = event.getX();
        touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d("TOUCH_EVENT" , "touch down");

                isTouchDown = true;
                if (!isServer) {
                    if (!isClientTouchDown) {
                        String direction = "null";

                        if (touchX < Math.floor(screenWidth / 2)) {
                            direction = "left";
                        } else {
                            direction = "right";
                        }

                        isClientTouchDown = true;
                        btClientUpdateTouch("true", direction);
                    }
                }

                break;

            case MotionEvent.ACTION_UP:
                Log.d("TOUCH_EVENT" , "touch up");

                isTouchDown = false;
                if (!isServer) {
                    if (isClientTouchDown) {
                        btClientUpdateTouch("false", "null");
                        isClientTouchDown = false;
                    }
                }

                break;
        }

        Log.d("TOUCH_EVENT", "xy: " + touchX + ", " + touchY);
    }

    private int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");

        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
    //endregion

    //region Getters for CustomView
    int getBatX() {
        return Math.round(batX * scaleX);
    }

    int getBatY() {
        return Math.round(batY * scaleY);
    }

    int getBatOppX() {
        return Math.round(batOppX * scaleX);
    }

    int getBatOppY() {
        return Math.round(batOppY * scaleY);
    }

    int getBatWidth() {
        return Math.round(batWidth * scaleX);
    }

    int getBatHeight() {
        return Math.round(batHeight * scaleY);
    }

    int getBallX() {
        return Math.round(ballX * scaleX);
    }

    int getBallY() {
        return Math.round(ballY * scaleY);
    }

    int getBallSide() {
        return Math.round(ballSide * scaleX);
    }

    float getScoreX() {
        return Math.round(scoreX * scaleX);
    }

    float getScoreY() {
        return Math.round(scoreY * scaleY);
    }

    int getScore() {
        return score;
    }

    int getScoreOpp() {
        return scoreOpp;
    }
    //endregion

    //region DEBUG
    boolean getDebug_show() {
        return DEBUG_show_debug;
    }

    // TODO: utterly horrible, but it's debug shit so who cares.
    String getDebug_msg(int i) {
        if (i == 1) return isServer ? "isServer TRUE" : "isServer FALSE";
        if (i == 2) return "ballX: " + ballX + " " + ballY;
        if (i == 3) return "batXY: " + batX + " " + batY + " batOppXY: " + batOppX + " " + batOppY;
        if (i == 4) return isClientTouchDown ? "isClientTD TRUE" : "isClientTD FALSE";
        if (i == 5) return DEBUG_sync_ball_buffer;
        if (i == 6) return DEBUG_sync_ball_diff;
        if (i == 7) return DEBUG_sync_bat_buffer;
        if (i == 8) return DEBUG_sync_bat_diff;
        return "debug returns nothing";
    }
    //endregion
}
