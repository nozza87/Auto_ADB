# Auto_ADB (+ ADB_Config)
An app that enables legacy ADB from within a device without user input + a tool (ADB Config) to help install the app and configure your device properly.

### Setup Instructions

if at any point you get errors, re-read this and try again or reboot your device and start again from step 1.  
If it still doesn't work then contact me with details and any error messages or logs.

(ADB Config is optional and all of the commands below can be run manually via adb commands if you want to [see "same as" sections below])  
(ADB Config just makes this much easier at will auto discover IP addresses and ports and run the comands to the correct device)

1. Extract "ADB_Config.zip" to a folder somewhere on your PC  
	adb binaries are included in the adb folder, feel free to replace with your own {tested using Android Debug Bridge version 1.0.41 [Version 36.0.0-13206524]}  
		(https://developer.android.com/tools/releases/platform-tools)

2. On your Android device go to 'Developer options' and enable both 'Wireless debugging' and 'USB Debugging' if they exist (select [Always Allow] if prompted on your device)

3. Run "ADB_Config.exe"  
	Press [h]' then [ENTER] to see the help prompt. (Also at bottom of this file)

4. Press [ENTER] or press [f] then [ENTER] to discover ADB devices (This will confirm everything is working)

5. Press [Control+C] to go back to the main menu of "ADB Config" then press [p] then [ENTER] to enter pairing mode.  
	This is the same as:  
		```adb pair -code-```

6. On your Android device go to 'Developer options -> Wireless debugging -> Pair device with pairing code' (select [Always Allow] if prompted on your device)

7. Enter the pairing code into "Auto ADB" and press [ENTER]

8. Press [I] then [ENTER] to install the "Auto ADB" app on your device (Select the included "Auto_ADB_v1.0.0.beta.apk" when prompted)
   {This will also grant the 'WRITE_SECURE_SETTINGS' and 'SYSTEM_ALERT_WINDOW' permissions.}  
	This is the same as:  
   ```adb connect ip:port```  
		```adb install "path_to_app.apk"```  
		```adb shell pm grant au.com.inoahguy.autoadb android.permission.WRITE_SECURE_SETTINGS``` 	{This allows "Auto ADB" to turn on 'USB Debugging' and 'Wireless Debugging'}  
		```adb shell pm grant au.com.inoahguy.autoadb android.permission.SYSTEM_ALERT_WINDOW```	{This allows "Auto ADB" to run it's main activity on boot without user interaction}  
		{The above command can also be done via the ui: go to 'settings' -> 'apps' -> 'special app access' -> 'display over other apps' and turn on 'Auto ADB'}  

10. On your Android device open "Auto ADB" and make sure both 'USB Debugging' and 'Wireless Debugging' toggles are on, Do NOT touch anything else yet! (select [Always Allow] if prompted on your device)

11. Press [L] then [ENTER] to enable legacy mode on your Android device (port 5555) (select [Always Allow] if prompted on your device)  
	This is the same as:  
		```adb connect ip:port```  
		```adb tcpip 5555```  

12. On your Android device in 'Auto ADB' press '[CONNECT LOCAL ADB]' (select [Always Allow] if prompted on your device)

13. If everything is ok up until this point then you can enable "[CONNECT LOCAL ADB] when opening the app" and "Start 'Auto ADB' on system boot"  
	These aren't required if you are happy to open the app and manually run '[CONNECT LOCAL ADB]' to re-enable legacy adb (port 5555)  

14. All going well your device should now have legacy adb running on port 5555 and should automatically enable it ater a reboot  
	Profit?

### Notes

* When using another app for the ADB client, beware that the ADB daemon (server) only starts if it's not *already running*, so this can cause conflicts with the embedded client on Auto ADB (if tcpip mode is activated), and the other app, since the authorized keys will **only** be loaded by the client that starts the ADB deamon, which can cause connection issues.

* The ADB daemon can be killed using 'adb kill-server'.

* Since the ADB TLS port is random each time, mDNS discovery is used in order to detect it within the app, if your network blocks this, this tool won't work.

* The 'legacy' mode, which allows unauthorized connections with an on-device prompt, needs an external device on the same network to set up for the first time, so that the embedded ADB client can be allowed to enable the mode by itself in the future.


### Setup Flow

| **Step** |        **Computer**        |           **Android Device**           |         **Driver**          |                   **Notes**                   |
|:--------:|:--------------------------:|:--------------------------------------:|:---------------------------:|:---------------------------------------------:|
|    1     |                            |          Manually Enable ADB           |                             | Steps 1 to 7 should only need to be done once |
|    2     |       Pair to Device       |                  <<<                   |                             |   Using ADB Wi-Fi pairing code & ADB Config   |
|    3     |     Connect to Device      |                                        |                             |     Automatically by ADB Config only once     |
|    4     |            >>>             | Accept System Popup(s) from ADB Config |                             |                   only once                   |
|    5     |      Push 'Auto ADB'       |                  <<<                   |                             |     Automatically by ADB Config only once     |
|    6     | Set 'Auto ADB' Permissions |                                        |                             |     Automatically by ADB Config only once     |
|    7     |       Run 'Auto ADB'       |                                        |                             |     Automatically by ADB Config only once     |
|    8     |            >>>             |  Accept System Popup(s) from Auto ADB  |                             |   On Reboot or Auth revoke, start from here   |
|    9     |                            |     'Auto ADB' enables legacy ADB      |                             |  Automatically by Auto ADB on open / reboot   |
|    10    |                            |                  >>>                   | Driver should run as normal |                                               |
|    11    |                            |   Accept System Popup(s) from driver   |             <<<             |                    If any?                    |
|    12    |                            |                Profit?                 |                             |                      :)                       |


### ADB Config Commands (same as [H]elp)

 - [F]ind:  
   * This will display any ADB devices found on the local network.  
   * This will run indefinitely, usefull for checking what state a device is in.

 - [S]how:  
   * This will show all currently connected ADB devices.

 - [D]isconnect:  
   * This will disconnect all currently connected ADB devices.

 - [P]air:  
   * This will Pair a TLS pairing device to this computer on this network using its Wi-Fi pairing code.  
   * This must be done before you can connect or restore to legacy.  
   * This is only required once per device per network unless revoked.  

 - [C]onnect:  
   * This will Connect a TLS device to this computer after it has been paired.  
   * This is required again after every device reboot or if revoked.  

 - [I]nstall:  
   * This will Install 'Auto ADB' to a connected device.  
   * This will also grant the 'WRITE_SECURE_SETTINGS' and 'SYSTEM_ALERT_WINDOW' permissions.  

 - [U]install:  
   * This will Uninstall 'Auto ADB' from a connected device.

 - [G]rant:  
   * This will grant the 'WRITE_SECURE_SETTINGS' and 'SYSTEM_ALERT_WINDOW' permissions that are required for 'Auto ADB' to run

 - [L]egacy:  
   * This will automatically restore a TLS device to a Legacy TCP connection so exisiting software can connect without anything extra.  
   * This is required again after every device reboot or if revoked.

 - [K]ill:  
   * This will kill the ADB server process  
   * This will disconnect all currently connected ADB devices.  
   * Try this if you are having connection issues.  
   Note: The next startup of this program will be delayed by a few seconds.

 - [H]elp:  
   * You've already figured this one out :)

 - [E]xit:  
   * Ends this program.

 - Press [Ctrl + C] at anytime to cancel
