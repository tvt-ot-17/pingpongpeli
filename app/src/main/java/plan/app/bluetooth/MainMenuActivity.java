package plan.app.bluetooth;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class MainMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
    }

    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.about:
                //startActivity(new Intent(this, About.class));
                Toast.makeText(MainMenuActivity.this, "About clicked",
                        Toast.LENGTH_LONG).show();
                return true;
            case R.id.help:
                //startActivity(new Intent(this, Help.class));
                Toast.makeText(MainMenuActivity.this, "Help clicked",
                        Toast.LENGTH_LONG).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void connectClicked(View v){
        Intent myIntent = new Intent(this, ConnectActivity.class);
        MainMenuActivity.this.startActivity(myIntent);
    }

    public void exit(View v){
        finish();
        System.exit(0);
    }

}
