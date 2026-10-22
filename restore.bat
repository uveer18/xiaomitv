@echo off
setlocal
echo Restoring Xiaomi TV to stock configuration...
set ADB="%~dp0platform-tools\adb.exe"
set TV_IP=192.168.29.28

%ADB% connect %TV_IP%:5555

echo Enabling stock launchers...
%ADB% -s %TV_IP%:5555 shell pm enable com.google.android.tvlauncher
%ADB% -s %TV_IP%:5555 shell pm enable com.mitv.tvhome.atv
%ADB% -s %TV_IP%:5555 shell cmd package set-home-activity --user 0 com.mitv.tvhome.atv/.app.BannerLogoA

echo Enabling background services and bloat...
%ADB% -s %TV_IP%:5555 shell pm enable com.miui.tv.analytics
%ADB% -s %TV_IP%:5555 shell pm enable com.xiaomi.statistic
%ADB% -s %TV_IP%:5555 shell pm enable com.xm.webcontent
%ADB% -s %TV_IP%:5555 shell pm enable com.mitv.tvhome.mitvplus
%ADB% -s %TV_IP%:5555 shell pm enable com.xiaomi.mitv.tvmanager
%ADB% -s %TV_IP%:5555 shell pm enable com.xiaomi.mitv.smartshare
%ADB% -s %TV_IP%:5555 shell pm enable com.mitv.milinkservice
%ADB% -s %TV_IP%:5555 shell pm enable com.xiaomi.mimusic2
%ADB% -s %TV_IP%:5555 shell pm enable com.google.android.play.games
%ADB% -s %TV_IP%:5555 shell pm enable com.google.android.feedback
%ADB% -s %TV_IP%:5555 shell pm enable com.google.android.tvrecommendations
%ADB% -s %TV_IP%:5555 shell pm enable com.xiaomi.android.tvsetup.partnercustomizer
%ADB% -s %TV_IP%:5555 shell pm enable com.xiaomi.mitv.handbook

echo Restoring animation scales...
%ADB% -s %TV_IP%:5555 shell settings put global window_animation_scale 1.0
%ADB% -s %TV_IP%:5555 shell settings put global transition_animation_scale 1.0
%ADB% -s %TV_IP%:5555 shell settings put global animator_duration_scale 1.0

%ADB% -s %TV_IP%:5555 shell input keyevent KEYCODE_HOME

echo Done! Stock TV setup restored.
pause
