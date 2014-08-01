#!/bin/sh

[ -z "$ANDROID_KEYSTORE_FILE" ] && ANDROID_KEYSTORE_FILE=~/.android/debug.keystore
[ -z "$ANDROID_KEYSTORE_ALIAS" ] && ANDROID_KEYSTORE_ALIAS=androiddebugkey

APPNAME=PrintPluginCups
APPVER=`grep android:versionName AndroidManifest.xml | sed 's/.*=//' | tr -d '"' | tr " '/" '---'`

stty -echo
jarsigner -verbose -keystore $ANDROID_KEYSTORE_FILE -sigalg MD5withRSA -digestalg SHA1 bin/MainActivity-release-unsigned.apk $ANDROID_KEYSTORE_ALIAS || exit 1
stty echo
echo
rm -f $APPNAME.apk
zipalign 4 bin/MainActivity-release-unsigned.apk $APPNAME.apk
