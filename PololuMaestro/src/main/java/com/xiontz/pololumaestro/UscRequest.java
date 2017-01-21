package com.xiontz.pololumaestro;

/**
 * Created by Rafal on 02/01/2017.
 */

enum UscRequest{
    REQUEST_GET_PARAMETER(0x81),
    REQUEST_SET_PARAMETER(0x82),
    REQUEST_GET_VARIABLES(0x83),
    REQUEST_SET_SERVO_VARIABLE(0x84),
    REQUEST_SET_TARGET(0x85),
    REQUEST_CLEAR_ERRORS(0x86),
    REQUEST_GET_SERVO_SETTINGS(0x87),

    // GET STACK and GET CALL STACK are only used on the Mini Maestro.
    REQUEST_GET_STACK(0x88),
    REQUEST_GET_CALL_STACK(0x89),
    REQUEST_SET_PWM(0x8A),

    REQUEST_REINITIALIZE(0x90),
    REQUEST_ERASE_SCRIPT(0xA0),
    REQUEST_WRITE_SCRIPT(0xA1),
    REQUEST_SET_SCRIPT_DONE(0xA2), // value.low.b is 0 for go, 1 for stop, 2 for single-step
    REQUEST_RESTART_SCRIPT_AT_SUBROUTINE(0xA3),
    REQUEST_RESTART_SCRIPT_AT_SUBROUTINE_WITH_PARAMETER(0xA4),
    REQUEST_RESTART_SCRIPT(0xA5),
    REQUEST_START_BOOTLOADER(0xFF);

    private int value;
    private UscRequest(int value){
        this.value = value;
    }
    public byte getValue() {
        return (byte)value;
    }
}