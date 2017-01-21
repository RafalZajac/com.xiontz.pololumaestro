package com.xiontz.pololumaestro;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.support.v4.content.res.TypedArrayUtils;
import android.util.Log;
import com.felhr.usbserial.UsbSerialDevice;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MaestroSerial {


    public interface OnStateChangeListener {
        void onMaestroAttached();
        void onMaestroDetached();
        void onMaestroReady();
        void onMaestroConnectionError();
        void onMaestroMovingStateChange(boolean anyServosMoving);
        void onMaestroServoPositionChanged();
    }

    private static final String TAG = "MaestroSerial";
    private final UsbManager usbManager;
    private UsbSerialDevice serial;
    private UsbDeviceConnection connection;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    public void setDefaultTimeoutForSyncCommands(int defaultTimeoutForSyncCommands) {
        this.defaultTimeoutForSyncCommands = defaultTimeoutForSyncCommands;
    }

    private int defaultTimeoutForSyncCommands = 500;

    public void setEventListener(OnStateChangeListener eventListener) {
        this.eventListener = eventListener;
    }

    private OnStateChangeListener eventListener;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        connection = usbManager.openDevice(device);
                        if(device != null){
                            serial = UsbSerialDevice.createUsbSerialDevice(device, connection);
                            serial.syncOpen();
                            if(eventListener!=null)
                                eventListener.onMaestroReady();
                        }
                    }
                    else {
                        //access to USB was denied by user
                        if(eventListener!=null)
                            eventListener.onMaestroConnectionError();
                    }
                }
            }
        }
    };

    private final BroadcastReceiver attachDetachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)){
                if(eventListener!=null)
                    eventListener.onMaestroAttached();

                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                context.registerReceiver(mUsbReceiver, filter);
                HashMap<String,UsbDevice> devices = usbManager.getDeviceList();
                UsbDevice device = devices.get(devices.keySet().iterator().next());
                usbManager.requestPermission(device, mPermissionIntent);
            }
            else if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)){
                if(eventListener!=null)
                    eventListener.onMaestroDetached();
            }

        }
    };

    MaestroSerial(Activity parentActivity){
        usbManager = (UsbManager) parentActivity.getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        parentActivity.registerReceiver(attachDetachReceiver, filter);
    }

    private byte[] targetToLoHiBits(int target){
        return new byte[]{(byte)((target * 4) & 0x7F), (byte)(((target * 4) >> 7) & 0x7F)};
    }

    private byte[] concatByteArrays(byte[] a, byte[] b){
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private void syncWriteCommand(int command, byte channel, int target){
        serial.syncWrite(concatByteArrays(new byte[]{(byte)command, channel},targetToLoHiBits(target)),defaultTimeoutForSyncCommands);
    }

    private void syncWriteCommand(int command, byte channel){
        serial.syncWrite(new byte[]{(byte)command, channel},defaultTimeoutForSyncCommands);
    }

    private void syncWriteCommand(int command){
        serial.syncWrite(new byte[]{(byte)command},defaultTimeoutForSyncCommands);
    }

    public void setTarget(byte channel, int target){
        syncWriteCommand(0x84, channel, target);
    }

    public void setMultipleTargets(byte firstChannelNumber, int[] targets){
        if(targets == null || targets.length==0) return;
        byte numberOfTargets = (byte)targets.length;
        byte[] startBytes = new byte[] { (byte)0x9F, numberOfTargets, firstChannelNumber};
        byte[] valueBytes = new byte[targets.length*2];
        for (int i = 0; i < targets.length*2; i+=2)
        {
            byte[] loHi = targetToLoHiBits(targets[i]);
            valueBytes[i] = loHi[0];
            valueBytes[i+1] = loHi[1];
        }
        serial.write(concatByteArrays(startBytes,valueBytes));
    }

    public void setSpeed(byte channel, int speed) {
        syncWriteCommand(0x87, channel, speed);
    }
    public void resetAllSpeed(int speed) {
        for(byte channel=0;channel<24;channel++)
            setSpeed(channel,0);
    }

    public void setAcceleration(byte channel, int acceleration) {
        syncWriteCommand(0x89, channel, acceleration);
    }

    public int getPosition(byte channel) {
        syncWriteCommand(0x90, channel);
        byte[] buffer = new byte[2];
        serial.syncRead(buffer, 2);
        return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getShort()/4;
    }

    public boolean getMovingState() {
        syncWriteCommand(0x93);
        byte[] buffer = new byte[1];
        serial.syncRead(buffer, 2);
        return buffer[0]==0x01;
    }

    public String[][] getErrors() {
        syncWriteCommand(0xA1);
        byte[] buffer = new byte[2];
        serial.syncRead(buffer, 2);
        short errors = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getShort();
        ArrayList<String[]> outStrings = new ArrayList<>();
        for (int i =0; i<16;i++){
            if((errors & i)>0)
                outStrings.add(errorMessages[i]);
        }
        return (String[][]) outStrings.toArray();
    }

    public void goHome() {
        syncWriteCommand(0xA2);
    }

    private static final String[][] errorMessages = {
            {
                    "Serial Signal Error",
                    "A hardware-level error that occurs when a byte’s stop bit is not detected at the expected place. This can occur if you are communicating at a baud rate that differs from the Maestro’s baud rate."
            },
            {
                    "Serial Overrun Error",
                    "A hardware-level error that occurs when the UART’s internal buffer fills up. This should not occur during normal operation."
            },
            {
                    "Serial RX buffer full",
                    "A firmware-level error that occurs when the firmware’s buffer for bytes received on the RX line is full and a byte from RX has been lost as a result. This error should not occur during normal operation."
            },
            {
                    "Serial CRC error",
                    "This error occurs when the Maestro is running in CRC-enabled mode and the cyclic redundancy check (CRC) byte at the end of the command packet does not match what the Maestro has computed as that packet’s CRC (Section 5.d). In such a case, the Maestro ignores the command packet and generates a CRC error."
            },
            {
                    "Serial protocol error",
                    "This error occurs when the Maestro receives an incorrectly formatted or nonsensical command packet. For example, if the command byte does not match a known command or an unfinished command packet is interrupted by another command packet, this error occurs."
            },
            {
                    "Serial timeout",
                    "When the serial timeout is enabled, this error occurs whenever the timeout period has elapsed without the Maestro receiving any valid serial commands. This timeout error can be used to make the servos return to their home positions in the event that serial communication between the Maestro and its controller is disrupted."
            },
            {
                    "Script stack error",
                    "This error occurs when a bug in the user script has caused the stack to overflow or underflow. Any script command that modifies the stack has the potential to cause this error. The stack depth is 32 on the Micro Maestro and 126 on the Mini Maestros."
            },
            {
                    "Script call stack error",
                    "This error occurs when a bug in the user script has caused the call stack to overflow or underflow. An overflow can occur if there are too many levels of nested subroutines, or a subroutine calls itself too many times. The call stack depth is 10 on the Micro Maestro and 126 on the Mini Maestros. An underflow can occur when there is a return without a corresponding subroutine call. An underflow will occur if you run a subroutine using the “Restart Script at Subroutine” serial command and the subroutine terminates with a return command rather than a quit command or an infinite loop."
            },
            {
                    "Script program counter error",
                    "This error occurs when a bug in the user script has caused the program counter (the address of the next instruction to be executed) to go out of bounds. This can happen if your program is not terminated by a quit, return, or infinite loop."
            },
    };
/*
    private class ServoPositionMonitor extends AsyncTask<MaestroSerial, Integer, String> {

        MaestroSerial maestro;
        int channelCount = 24;

        int[] servoPositions;

        ServoPositionMonitor(MaestroSerial maestro){
            maestro = maestro;
            servoPositions = getPositions();
        }

        private int[] getPositions(){
            int[] ret = new int[channelCount];
            for (byte i = 0; i < channelCount;i++){
                ret[i] = maestro.getPosition(i);
            }
            return ret;
        }

        @Override
        protected String doInBackground(MaestroSerial... params) {
            boolean anyChanged = false;
            int[] newPositions = new int[channelCount];
            boolean change = false;
            for (byte i = 0; i<24;i++) {
                if (servoPositions[i] != newPositions[i])
                    change = true;
            }
            servoPositions = newPositions;

        }
    }
*/
}
