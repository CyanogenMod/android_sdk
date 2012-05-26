<manifest xmlns:android="http://schemas.android.com/apk/res/android" >

    <application>

        <activity android:name=".${activityClass}"
            android:label="@string/activity_name">
            <intent-filter android:label="@string/activity_name">
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
