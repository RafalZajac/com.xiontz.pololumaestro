package com.xiontz.pololumaestro;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, MaestroUsc.OnMaestroStateChangeListener, AdapterView.OnItemSelectedListener {
    TextView twDeviceinfo;
    TextView tw;
    TextView tw3;
    SeekBar sb;
    SeekBar sb2;
    Spinner spinnerServoChannels;
    int selectedServoChannel=0;

    //MaestroSerial maestro;
    MaestroUsc maestroUsc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tw=(TextView) findViewById(R.id.testText);
        tw.setText("");
        tw3=(TextView) findViewById(R.id.textView);
        twDeviceinfo = (TextView) findViewById(R.id.twDeviceInfo);
        sb=(SeekBar)   findViewById(R.id.seekBar);
        sb.setEnabled(false);
        sb.setOnSeekBarChangeListener(this);
        spinnerServoChannels = (Spinner)   findViewById(R.id.spinnerServoChannels);
        spinnerServoChannels.setOnItemSelectedListener(this);
        sb2=(SeekBar)   findViewById(R.id.seekBar2);
        sb2.setEnabled(false);

        maestroUsc = new MaestroUsc(this);
        maestroUsc.setEventListener(this);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {


    }
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        try{
            maestroUsc.setTarget(0,sb.getProgress()+672);
        }catch(Exception e){
            tw.append(e.getMessage());
        }
    }

    @Override
    public void onDeviceAttached() {
        tw.append("attached...\n");
    }

    @Override
    public void onDeviceDetached() {
        tw.append("detached...\n");
        sb.setEnabled(false);
    }

    @Override
    public void onDeviceReady() {
        tw.append("ready...\n");
        byte[] ver =  maestroUsc.getFirmwareVersion();
        twDeviceinfo.setText("Device: Pololu Maestro "+ maestroUsc.getServoCount()+"\n");
        twDeviceinfo.append("serial no: "+ maestroUsc.getSerialNumber()+"\n");
        twDeviceinfo.append("firmware ver.:"+ver[0]+"."+ver[1]+"\n");
        maestroUsc.setSpeed(0,0);
        maestroUsc.setAcceleration(0,0);
        setServoChannels();
        sb.setEnabled(true);
    }

    private void setServoChannels(){
        ArrayAdapter<Integer> adapter;
        List<Integer> list;

        list = new ArrayList<Integer>();
        list.add(0);
        list.add(1);
        list.add(2);
        adapter = new ArrayAdapter<Integer>(getApplicationContext(),
                android.R.layout.simple_spinner_item, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.setNotifyOnChange(true);
        spinnerServoChannels.setAdapter(adapter);
    }

    @Override
    public void onDeviceConnectionError(String message) {
        tw.append("error:"+message+"\n");
    }

    @Override
    public void onServosMovingChange(boolean anyServosMoving) {
        tw.append("moving state changed...\n");
    }

    @Override
    public void onServoPositionChanged(MaestroUsc.ServoOnTheMove[] servos) {
        tw.setText("pos: "+(servos[0].newPosition-672+"\n"));
        sb2.setProgress(servos[0].newPosition-672);
    }

    @Override
    public void onServoPositionMonitoringStopped(String message) {
        tw.append("MONITORING STOPPED: "+message+"\n");
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        selectedServoChannel = position;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
