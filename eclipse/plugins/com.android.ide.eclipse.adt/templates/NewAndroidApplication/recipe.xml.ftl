<?xml version="1.0"?>
<recipe>
    <instantiate from="AndroidManifest.xml.ftl" />

<#if copyIcons>
    <copy from="res/drawable-hdpi" />
    <copy from="res/drawable-mdpi" />
    <copy from="res/drawable-xhdpi" />
</#if>
    <copy from="res/values/dimens.xml" />
    <copy from="res/values/styles.xml" />
    <copy from="res/values-large/dimens.xml" />

    <instantiate from="res/values/strings.xml.ftl" />
</recipe>
