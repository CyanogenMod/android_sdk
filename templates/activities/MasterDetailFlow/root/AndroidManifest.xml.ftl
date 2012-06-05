<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity android:name=".${CollectionName}Activity"
            android:label="@string/title_${collection_name}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name=".${DetailName}Activity"
            android:label="@string/title_${detail_name}" />
    </application>

</manifest>
