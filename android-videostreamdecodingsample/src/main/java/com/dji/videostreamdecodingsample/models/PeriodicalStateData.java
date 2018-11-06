package com.dji.videostreamdecodingsample.models;
public class PeriodicalStateData {
    private int AircraftBattery=-1;
    private int RemoteControllerBattery=-1;
    private int flightControllerGPSSatelliteCount=-1;
    private int remoteControllerSwitchMode=-1;
    private int aircraftBatteryPercentageNeededToGoHome=-1;
    private int flightTime=-1;
    private boolean sensorBeingUsedFlightAssistant=false;
    private boolean firstReading=false;

    public boolean isFirstReading() {
        return firstReading;
    }
    public void setFirstReading(boolean firstReading) {
        this.firstReading = firstReading;
    }
    public int getAircraftBattery() {
        return AircraftBattery;
    }

    public void setAircraftBattery(int aircraftBattery) {
        AircraftBattery = aircraftBattery;
    }

    public int getRemoteControllerBattery() {
        return RemoteControllerBattery;
    }

    public void setRemoteControllerBattery(int remoteControllerBattery) {
        RemoteControllerBattery = remoteControllerBattery;
    }

    public int getFlightControllerGPSSatelliteCount() {
        return flightControllerGPSSatelliteCount;
    }

    public void setFlightControllerGPSSatelliteCount(int flightControllerGPSSatelliteCount) {
        this.flightControllerGPSSatelliteCount = flightControllerGPSSatelliteCount;
    }

    public int getRemoteControllerSwitchMode() {
        return remoteControllerSwitchMode;
    }

    public void setRemoteControllerSwitchMode(int remoteControllerSwitchMode) {
        this.remoteControllerSwitchMode = remoteControllerSwitchMode;
    }
    public boolean isSensorBeingUsedFlightAssistant() {
        return sensorBeingUsedFlightAssistant;
    }

    public void setSensorBeingUsedFlightAssistant(boolean sensorBeingUsedFlightAssistant) {
        this.sensorBeingUsedFlightAssistant = sensorBeingUsedFlightAssistant;
    }
    public int getAircraftBatteryPercentageNeededToGoHome() {
        return aircraftBatteryPercentageNeededToGoHome;
    }

    public void setAircraftBatteryPercentageNeededToGoHome(int aircraftBatteryPercentageNeededToGoHome) {
        this.aircraftBatteryPercentageNeededToGoHome = aircraftBatteryPercentageNeededToGoHome;
    }
    public int getFlightTime() {
        return flightTime;
    }

    public void setFlightTime(int flightTime) {
        this.flightTime = flightTime;
    }
}
