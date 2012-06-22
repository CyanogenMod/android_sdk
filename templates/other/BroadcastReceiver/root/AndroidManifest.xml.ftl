<manifest xmlns:android="http://schemas.android.com/apk/res/android" >

    <receiver>
        <service android:name=".${className}"
            android:exported="${isExported?string}"
            android:enabled="${isEnabled?string}" >
        </service>
    </receiver>

</manifest>
