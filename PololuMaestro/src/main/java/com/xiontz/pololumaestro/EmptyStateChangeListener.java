package com.xiontz.pololumaestro;

/**
 * Created by Rafal on 02/01/2017.
 */

class EmptyStateChangeListener implements MaestroUsc.OnMaestroStateChangeListener {

    @Override
    public void onDeviceAttached() {

    }

    @Override
    public void onDeviceDetached() {

    }

    @Override
    public void onDeviceReady() {

    }

    @Override
    public void onDeviceConnectionError(String message) {

    }

    @Override
    public void onServosMovingChange(boolean anyServosMoving) {

    }

    @Override
    public void onServoPositionChanged(MaestroUsc.ServoOnTheMove[] servos) {

    }
    @Override
    public void onServoPositionMonitoringStopped(String message){

    }
}