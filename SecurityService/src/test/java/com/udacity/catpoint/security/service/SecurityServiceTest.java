package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.FakeImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private SecurityService securityService;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private FakeImageService fakeImageService;

    @Mock
    private StatusListener statusListener;

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, fakeImageService);
    }


    @DisplayName("changeSensorActivationStatus")
    @CsvFileSource(resources = "/changeSensorActivationStatus.csv", numLinesToSkip = 1, nullValues = {"NULL"})
    @ParameterizedTest(name = "changeSensorActivationStatus_{0}")
    void changeSensorActivationStatus(String testNo) throws Exception {
        // SETUP INPUT
        Sensor input = new Sensor("Sensor", SensorType.DOOR);
        switch (testNo) {
            case "alarmArmedAndSensorActivated_alarmStatusPending":
            case "alarmAlreadyPendingAndSensorActivated_alarmStatusAlarm":
            case "alarmPendingAndAllSensorsInactive_changeToNoAlarm":
                Mockito.doReturn(ArmingStatus.ARMED_HOME).when(securityRepository).getArmingStatus();
                switch (testNo) {
                    case "alarmArmedAndSensorActivated_alarmStatusPending":
                        // If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
                        Mockito.doReturn(AlarmStatus.NO_ALARM).when(securityRepository).getAlarmStatus();
                        break;
                    case "alarmAlreadyPendingAndSensorActivated_alarmStatusAlarm":
                        // If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
                        Mockito.doReturn(AlarmStatus.PENDING_ALARM).when(securityRepository).getAlarmStatus();
                        break;
                    case "alarmPendingAndAllSensorsInactive_changeToNoAlarm":
                        // If pending alarm and all sensors are inactive, return to no alarm state.
                        input.setActive(false);
                        Mockito.doReturn(AlarmStatus.PENDING_ALARM).when(securityRepository).getAlarmStatus();
                        break;
                }
                break;
            case "alarmActiveAndSensorStateChanges_stateNotAffected":
                // If alarm is active, change in sensor state should not affect the alarm state.
                Mockito.doReturn(AlarmStatus.ALARM).when(securityRepository).getAlarmStatus();
                break;
            case "systemActivatedWhileAlreadyActiveAndAlarmPending_changeToAlarmState":
            case "sensorDeactivateWhileInactive_noChangeToAlarmState":
                switch (testNo) {
                    case "systemActivatedWhileAlreadyActiveAndAlarmPending_changeToAlarmState":
                        // If a sensor is activated while already active and the system is in pending state, change it to alarm state.
                        input.setActive(true);
                        break;
                    case "sensorDeactivateWhileInactive_noChangeToAlarmState":
                        // If a sensor is deactivated while already inactive, make no changes to the alarm state.
                        input.setActive(false);
                        break;
                }
                Mockito.doReturn(AlarmStatus.PENDING_ALARM).when(securityRepository).getAlarmStatus();
                break;
        }

        try {

            // EXECUTE METHOD
            switch (testNo) {
                case "alarmPendingAndAllSensorsInactive_changeToNoAlarm":
                    securityService.changeSensorActivationStatus(input, true);
                    securityService.changeSensorActivationStatus(input, false);
                    break;
                case "alarmStatusPendingActive_sensorsInactive_alarmStatusPending":
                    securityService.changeSensorActivationStatus(input, false);
                    break;
                default:
                    securityService.changeSensorActivationStatus(input, true);
                    break;
            }

            // VERIFY
            switch (testNo) {
                case "alarmArmedAndSensorActivated_alarmStatusPending":
                    Mockito.verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
                    break;
                case "alarmAlreadyPendingAndSensorActivated_alarmStatusAlarm":
                case "systemActivatedWhileAlreadyActiveAndAlarmPending_changeToAlarmState":
                    Mockito.verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
                    break;
                case "alarmPendingAndAllSensorsInactive_changeToNoAlarm":
                    Mockito.verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
                    break;
                case "alarmActiveAndSensorStateChanges_stateNotAffected":
                case "sensorDeactivateWhileInactive_noChangeToAlarmState":
                    Mockito.verify(securityRepository, never()).setAlarmStatus(AlarmStatus.NO_ALARM);
                    Mockito.verify(securityRepository, never()).setAlarmStatus(AlarmStatus.PENDING_ALARM);
                    break;
            }

        } catch (Exception ex) {
            throw ex;
        }
    }

    @DisplayName("processImage")
    @CsvFileSource(resources = "/processImage.csv", numLinesToSkip = 1, nullValues = {"NULL"})
    @ParameterizedTest(name = "processImage_{0}")
    void processImage(String testNo) throws Exception {
        switch (testNo) {
            case "imageContainingCatDetectedAndSystemArmed_changeToAlarmStatus":
                // If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
                Mockito.doReturn(true).when(fakeImageService).imageContainsCat(Mockito.any(), Mockito.anyFloat());
                Mockito.doReturn(ArmingStatus.ARMED_HOME).when(securityRepository).getArmingStatus();
                break;
            case "noCatImageIdentifiedAndSensorsAreInactive_changeToAlarmStatus":
                // If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
                Sensor sensor = new Sensor("Sensor", SensorType.DOOR);
                sensor.setActive(false);
                Mockito.doReturn(false).when(fakeImageService).imageContainsCat(Mockito.any(), Mockito.anyFloat());
                break;
            case "systemArmedHomeAndCatDetected_changeToAlarmStatus":
                // If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
                Mockito.doReturn(true).when(fakeImageService).imageContainsCat(Mockito.any(), Mockito.anyFloat());
                Mockito.when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
                break;
        }

        try {
            // MOCK BufferedImage
            BufferedImage bufferedImageMock = Mockito.mock(BufferedImage.class);
            // EXECUTE METHOD
            securityService.processImage(bufferedImageMock);

            // VERIFY
            switch (testNo) {
                case "imageContainingCatDetectedAndSystemArmed_changeToAlarmStatus":
                case "systemArmedHomeAndCatDetected_changeToAlarmStatus":
                    Mockito.verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
                    break;
                case "noCatImageIdentifiedAndSensorsAreInactive_changeToAlarmStatus":
                    Mockito.verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
                    break;
            }
        } catch (Exception ex) {
            throw ex;
        }
    }

    @DisplayName("setArmingStatus")
    @CsvFileSource(resources = "/setArmingStatus.csv", numLinesToSkip = 1, nullValues = {"NULL"})
    @ParameterizedTest(name = "setArmingStatus_{0}")
    void setArmingStatus(String testNo) throws Exception {
        Set<Sensor> sensorSet = new HashSet<>();
        switch (testNo) {
            case "updateSensors_systemArmed_deactivateArmedAway":
            case "updateSensors_systemArmed_deactivateArmedAtHome":
                // If the system is armed, reset all sensors to inactive.
                sensorSet.add(new Sensor("Sensor_Door", SensorType.DOOR));
                sensorSet.add(new Sensor("Sensor_Window", SensorType.WINDOW));
                sensorSet.add(new Sensor("Sensor_Motion", SensorType.MOTION));
                Mockito.doReturn(AlarmStatus.PENDING_ALARM).when(securityRepository).getAlarmStatus();
                Mockito.doReturn(sensorSet).when(securityRepository).getSensors();
                for (Sensor sensor : sensorSet) {
                    sensor.setActive(true);
                }
                break;
        }
        try {
            // EXECUTE METHOD
            switch (testNo) {
                case "systemDisArmed_changeToAlarmStatus":
                    // If the system is disarmed, set the status to no alarm.
                    securityService.setArmingStatus(ArmingStatus.DISARMED);
                    break;
                case "updateSensors_systemArmed_deactivateArmedAway":
                    securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
                    break;
                case "updateSensors_systemArmed_deactivateArmedAtHome":
                    securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
                    break;
            }

            // VERIFY
            switch (testNo) {
                case "systemDisArmed_changeToAlarmStatus":
                    Mockito.verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
                    break;
                case "updateSensors_systemArmed_deactivateArmedAway":
                case "updateSensors_systemArmed_deactivateArmedAtHome":
                    for (Sensor sensor : sensorSet) {
                        assertFalse(sensor.getActive());
                    }
                    break;
            }
        } catch (Exception ex) {
            throw ex;
        }
    }

    @Test
    @DisplayName("addStatusListener")
    void addStatusListener() throws Exception {
        try {
            // EXECUTE METHOD
            securityService.addStatusListener(statusListener);
        } catch (Exception ex) {
            throw ex;
        }
    }

    @Test
    @DisplayName("removeStatusListener")
    void removeStatusListener() throws Exception {
        try {
            // EXECUTE METHOD
            securityService.removeStatusListener(statusListener);
        } catch (Exception ex) {
            throw ex;
        }
    }

    @Test
    @DisplayName("addSensor")
    void addSensor() throws Exception {
        try {
            // SETUP INPUT
            Sensor sensor = new Sensor("Sensor", SensorType.DOOR);
            // EXECUTE METHOD
            securityService.addSensor(sensor);
        } catch (Exception ex) {
            throw ex;
        }
    }

    @Test
    @DisplayName("removeSensor")
    void removeSensor() throws Exception {
        try {
            // SETUP INPUT
            Sensor sensor = new Sensor("Sensor", SensorType.DOOR);
            // EXECUTE METHOD
            securityService.removeSensor(sensor);
        } catch (Exception ex) {
            throw ex;
        }
    }

    @Test
    void alarmStatusPendingActive_sensorsInactive_alarmStatusPending(){
        Mockito.when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.handleSensorDeactivated();
        Mockito.verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }
}
