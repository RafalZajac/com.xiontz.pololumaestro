package com.xiontz.pololumaestro;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Rafal on 31/12/2016.
 */

public class MaestroUsc
{
    private static final String TAG = "MaestroUsc";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private final Context context;
    private final UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private ServoPositionMonitor servoPositionMonitor;
    private HashMap<Integer,Integer> servosInProduct = new HashMap<Integer, Integer>(){{
        put(0x89,6);
        put(0x8A,12);
        put(0x8B,18);
        put(0x8C,24);
    }};

    public interface OnMaestroStateChangeListener {
        void onDeviceAttached();
        void onDeviceDetached();
        void onDeviceReady();
        void onDeviceConnectionError(String message);
        void onServosMovingChange(boolean anyServosMoving);
        void onServoPositionChanged(ServoOnTheMove[] servos);
        void onServoPositionMonitoringStopped(String message);
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        try {
                            device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                            if (device == null) {
                                eventListener.onDeviceConnectionError("Device not initialised.");
                                return;
                            }
                            if (device.getVendorId() != 8187 || !servosInProduct.containsKey(device.getProductId())) {
                                eventListener.onDeviceConnectionError("Device not recognised. Vendor ID: " + device.getVendorId() + ", product ID:" + device.getProductId() + ".");
                                return;
                            }
                            connection = usbManager.openDevice(device);
                            startServoMonitor();
                            eventListener.onDeviceReady();
                        }catch(Exception e){
                            eventListener.onDeviceConnectionError(e.getMessage());
                        }
                    }
                    else {
                        //access to USB was denied by user
                        eventListener.onDeviceConnectionError("User denied access to to the device.");
                    }
                }
            }
        }
    };

    private final BroadcastReceiver attachDetachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)){
                eventListener.onDeviceAttached();

                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                context.registerReceiver(mUsbReceiver, filter);
                HashMap<String,UsbDevice> devices = usbManager.getDeviceList();
                UsbDevice device = devices.get(devices.keySet().iterator().next());
                usbManager.requestPermission(device, mPermissionIntent);
            }
            else if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)){
                eventListener.onDeviceDetached();
                servoPositionMonitor.cancel(true);
            }

        }
    };

    private int servoCount;
    public int getServoCount(){
        if(servoCount>0) return servoCount;
        servoCount =  servosInProduct.get(getProductId()).intValue();
        return servoCount;
    }

    private OnMaestroStateChangeListener eventListener = new EmptyStateChangeListener();
    public void setEventListener(OnMaestroStateChangeListener eventListener) {
        this.eventListener = eventListener;
    }

    private int defaultTimeout = 500;
    public int getDefaultTimeout(){
        return defaultTimeout;
    }
    public void setDefaultTimeout(int value){
        defaultTimeout = value;
    }


    MaestroUsc(Context ctx){
        context = ctx;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(attachDetachReceiver, filter);

    }
    private void startServoMonitor(){
        servoPositionMonitor = new ServoPositionMonitor();
        servoPositionMonitor.execute(this);
    }
    public int getVendorId(){
        return device.getVendorId();
    }

    public int getProductId(){
        return device.getProductId();
    }

    public String getSerialNumber(){
        return device.getSerialNumber();
    }

    private int controlTransfer(int requestType, int request, int value, int index, byte[] buffer){
        return connection.controlTransfer(requestType, request, value, index, buffer,buffer==null?0:buffer.length,defaultTimeout);
    }
    private int controlTransfer(int requestType, int request, int value,int index){
        return controlTransfer(requestType, request, value, index, null);
    }
    private int controlTransfer(int requestType, int request, int value){
        return controlTransfer(requestType, request, value, 0);
    }
    private int controlTransfer(int requestType, int request, int value,byte[] buffer){
        return controlTransfer(requestType, request, value, 0,buffer);
    }

    public byte[] getFirmwareVersion(){
        byte[] buffer = new byte[14];
        controlTransfer(0x80, 6, 0x0100, buffer);

        byte minor = (byte)((buffer[12] & 0xF) + (buffer[12] >> 4 & 0xF) * 10);
        byte major = (byte)((buffer[13] & 0xF) + (buffer[13] >> 4 & 0xF) * 10);
        return new byte[]{major,minor};
    }

    public void setTarget(int servo, int value){
        controlTransfer(0x40, UscRequest.REQUEST_SET_TARGET.getValue(), value*4, servo);
    }

    public void setSpeed(int servo, int value)
    {
        controlTransfer(0x40, UscRequest.REQUEST_SET_SERVO_VARIABLE.getValue(), value, servo);
    }

    public void setAcceleration(int servo, int value)
    {
        // set the high bit of servo to specify acceleration
        controlTransfer(0x40, UscRequest.REQUEST_SET_SERVO_VARIABLE.getValue(),
            value, (byte)(servo | 0x80));
    }

    public ServoStatus[] getServoStatus() throws Exception {
        /*
        expecting 4 values:
            - position: 2 bytes (short)
            - target: 2 bytes (short)
            - speed: 2 bytes (short)
            - acceleration: 1 byte (byte)
            all together 7 bytes per servo.
         */
        int rawBytesPerServo = 7;
        byte[] bytes = new byte[getServoCount() * rawBytesPerServo];

        // Get the raw data from the device.
        int bytesRead = controlTransfer(0xC0, UscRequest.REQUEST_GET_SERVO_SETTINGS.getValue(), 0, 0, bytes);
        if(bytesRead<bytes.length)
            throw new Exception("Not enough data from the device.");

        ServoStatus[] ret = new ServoStatus[getServoCount()];
        //ByteBuffer bb = ByteBuffer.wrap(servoSettingsArray);

        for (int i =0; i<getServoCount();i++){
            ServoStatus servo = new ServoStatus();
            servo.position = extractShort(bytes,i*7)/4;
            servo.target = extractShort(bytes,i*7+2)/4;
            servo.speed = extractShort(bytes,i*7+4);
            servo.acceleration = bytes[i*7+6];
            ret[i] = servo;
        }
        return ret;
    }

    private int extractShort(byte[] bytes, int fromIndex){
        return (bytes[fromIndex+1]<<8) + bytes[fromIndex];
    }

    public class ServoStatus{
        int position;
        int target;
        int speed;
        byte acceleration;
    }

    public class ServoOnTheMove{
        int servoIndex;
        int oldPosition;
        int newPosition;
        int target;
    }

    public class ServoPositionMonitor extends AsyncTask<MaestroUsc, ServoOnTheMove, String> {
        private volatile boolean running = true;
        private int consecutiveErrors = 0;
        ServoStatus[] previousServos;
        @Override
        protected String doInBackground(MaestroUsc... params) {
            MaestroUsc maestro = params[0];
            while (running){
                try {
                    ServoStatus[] servos = maestro.getServoStatus();
                    if(previousServos==null)
                        previousServos = servos;
                    List<ServoOnTheMove> movingServos = new ArrayList<ServoOnTheMove>();
                    boolean anyMoving = false;
                    for(int i=0;i<servos.length;i++){
                        if(servos[i].position!=previousServos[i].position){
                            anyMoving = true;
                            ServoOnTheMove s = new ServoOnTheMove();
                            s.servoIndex = i;
                            s.oldPosition = previousServos[i].position;
                            s.newPosition = servos[i].position;
                            s.target = servos[i].target;
                            movingServos.add(s);
                        }
                    }
                    previousServos = servos;

                    publishProgress(movingServos.toArray(new ServoOnTheMove[movingServos.size()]));
                    consecutiveErrors = 0;
                } catch (Exception e) {
                    consecutiveErrors++;
                    if(consecutiveErrors>10){
                        return "Error: "+e.getMessage();
                    }
                }
            }

            return "All dandy.";
        }

        @Override
        protected void onCancelled() {
            running = false;
        }

        @Override
        protected void onProgressUpdate(ServoOnTheMove... servos) {
            for (int i=0;i<servos.length;i++)
                eventListener.onServoPositionChanged(servos);
        }

        @Override
        protected void onPostExecute(String message) {
            eventListener.onServoPositionMonitoringStopped(message);
        }
    }


}
