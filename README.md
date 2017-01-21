# Pololu Maestro on Android

**Please note:** This code is just a proof of concept and will not be maintained. It has been uploaded only to record the results of the proof of concept.

It should be a working sample in a sense that it should be possible to compile and deploy the app to the android device, connect the Pololu Maestro vis the USB OTG connection and control the servo connected to the chanel no. 0.

The code can be used as an example of 2 different ways of communication from the android device to Pololu Maestro servo controller: serial and USC.

There are 2 classes in the project which contain partial implementation of serial nad USC commands: MaestroSerial and MaestroUsc.

##Serial 
To add remaining serial commands please refer to Pololu documentation: https://www.pololu.com/docs/0J40/5. 
Serial communication protocol is quite well documented but allows only to control basic functions of the controller e.g. setting servo positions, playing pre-recorded scripts etc. 

##USC
Pololu doesn't seem to provide the documentation of how to use USC to access full functionality of the controller. The only way I found was to check an example code for .NET which is part of Pololu SDK (https://www.pololu.com/docs/0J41).

The class MaestroUsc is an attempt to transfer logic from the sample C# project which location within the SDK is following: pololu-usb-sdk\Maestro\Usc\Usc.proj 

