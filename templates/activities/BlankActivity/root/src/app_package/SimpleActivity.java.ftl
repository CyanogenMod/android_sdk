package ${packageName};

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class ${activityClass} extends Activity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.${menuName}, menu);
        return true;
    }
}
