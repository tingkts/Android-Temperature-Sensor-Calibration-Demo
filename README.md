# Temperature-Sensor-Calibration-Demo
just a test for the demo of t-sensor calibration



Requirements:

1.	Create an Activity,
2.	When this Activity started, please disable the P-sensor.
3.	Then turn of the LCD.
4.	Then read the Temperature(from temperature sensor) every 5 minutes and record it(print to debug message).
5.	Check heat balance
6.	After about 30 minutes, connect to a server port 5773 using TCP.
7.	After connected, send command “GET_TEMP” to server.
8.	Then wait the server to send back “TEMP: xxx”.
9.	After receive the return from server, disconnect.
10.	Record the xxx and get the Temperature T again and record it.
11.	Compare the xxx and T and calculate the difference then record to a calibration file.


</br>
Apk sign debug platform key:
	Android Studio menu "Build" 
		-> "Generate Signed Bundle/APK"
		-> choose "APK"
		-> fill in fields, "Key store path", "Key stor password", "Key alias", "Key password"


</br>
Development environment:
	Android Studio 3.4
	Build #AI-183.5429.30.34.5452501, built on April 10, 2019
	JRE: 1.8.0_152-release-1343-b01 amd64
	JVM: OpenJDK 64-Bit Server VM by JetBrains s.r.o
	Windows 10 10.0
