<manifest xmlns:android="http://schemas.android.com/apk/res/android" >

    <application>
        <activity android:name=".${activityClass}"
            android:label="@string/title_${simpleName}"
            <#if buildApi gte 16 && parentActivityClass != "">android:parentActivityName="${parentActivityClass}"</#if>>
            <#if parentActivityClass != "">
            <meta-data android:name="android.support.PARENT_ACTIVITY"
                android:value="${parentActivityClass}" />
            </#if>
        </activity>
    </application>

</manifest>
