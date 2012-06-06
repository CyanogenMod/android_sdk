/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.deviceconfig;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import com.example.android.deviceconfig.R;

public class MyActivity extends Activity implements OnClickListener {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Button btn = (Button) findViewById(R.id.generateConfigButton);
        btn.setOnClickListener(this);
        Configuration config = getResources().getConfiguration();

        TextView tv = (TextView) findViewById(R.id.keyboard_state_api);
        if (tv != null) {
            String separator = config.orientation == Configuration.ORIENTATION_PORTRAIT ? "\n" : "";
            String foo = "keyboardHidden=" + separator;
            if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO) {
                foo += "EXPOSED";
            } else if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_YES) {
                foo += "HIDDEN";
            } else if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_UNDEFINED) {
                foo += "UNDEFINED";
            } else {
                foo += "?";
            }
            foo += "\nhardKeyboardHidden=" + separator;
            if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
                foo = foo + "EXPOSED";
            } else if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
                foo = foo + "HIDDEN";
            } else if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_UNDEFINED) {
                foo = foo + "UNDEFINED";
            } else {
                foo = "?";
            }

            tv.setText(foo);
        }

        tv = (TextView) findViewById(R.id.nav_state_api);
        if (tv != null) {
            if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_NO) {
                tv.setText("EXPOSED");
            } else if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_YES) {
                tv.setText("HIDDEN");
            } else if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_UNDEFINED) {
                tv.setText("UNDEFINED");
            } else {
                tv.setText("??");
            }
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();


        tv = (TextView) findViewById(R.id.size_api);
        if (tv != null) {
            int a = metrics.heightPixels;
            int b = metrics.widthPixels;
            tv.setText(b + "x" + a);
        }

        tv = (TextView) findViewById(R.id.xdpi);
        if (tv != null) {
            tv.setText(String.format("%f", metrics.xdpi));
        }
        tv = (TextView) findViewById(R.id.ydpi);
        if (tv != null) {
            tv.setText(String.format("%f", metrics.ydpi));
        }

        tv = (TextView) findViewById(R.id.scaled_density);
        if (tv != null) {
            tv.setText(String.format("%f", metrics.scaledDensity));
        }

        tv = (TextView) findViewById(R.id.font_scale);
        if (tv != null) {
            tv.setText(String.format("%f", config.fontScale));
        }

    }

    public void onClick(View v) {
        ConfigGenerator configGen = new ConfigGenerator(this);
        final String filename = configGen.generateConfig();
        if (filename != null) {
            Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
            emailIntent.setType("text/xml");
            File devicesXml = new File(filename);
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Device XML: " + devicesXml.getName());
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Note: This is intended to generate a base "
                    + "XML description. After running this, you should double check the generated "
                    + "information and add all of the missing fields.");
            emailIntent.putExtra(Intent.EXTRA_STREAM,
                    Uri.parse("file://" + devicesXml.getAbsolutePath()));
            startActivity(emailIntent);
        }
    }

}
