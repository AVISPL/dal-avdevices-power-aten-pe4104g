/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.avdevices.power.aten.pe4104g;

import static com.avispl.symphony.dal.util.ControllablePropertyFactory.createButton;
import static com.avispl.symphony.dal.util.ControllablePropertyFactory.createSwitch;

import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.security.auth.login.FailedLoginException;
import org.apache.commons.collections.CollectionUtils;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common.AtenPDUCommand;
import com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common.AtenPDUConstant;
import com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common.AtenPDUPropertiesEnum;
import com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common.OutletStatusEnum;
import com.avispl.symphony.dal.communicator.SshCommunicator;
import com.avispl.symphony.dal.util.StringUtils;

/**
 * Aten PDU Communicator Adapter an implementation of SshCommunicator to provide communication and interaction with Aten PDU device
 *
 * Supported feature are:
 *
 * Controlling Capabilities:
 * OutletStatus1 - On/Off
 * OutletStatus2 - On/Off
 * OutletStatus3 - On/Off
 * OutletStatus4 - On/Off
 * Reboot
 *
 * @author Kevin / Symphony Dev Team<br>
 * Created on 1/18/2024
 * @since 1.0.0
 */
public class AtenPDUCommunicator extends SshCommunicator implements Monitorable, Controller {

	/**
	 * cache to store key and value
	 */
	private final Map<String, String> cacheKeyAndValue = new HashMap<>();

	/**
	 * count the failed command
	 */
	private final Map<String, String> failedMonitor = new HashMap<>();

	/**
	 * Prevent case where {@link AtenPDUCommunicator#controlProperty(ControllableProperty)} slow down -
	 * the getMultipleStatistics interval if it's fail to execute the cmd
	 */
	private static final int controlSSHTimeout = 3000;

	/**
	 * Set back to default timeout value in {@link SshCommunicator}
	 */
	private static final int statisticsSSHTimeout = 30000;

	/**
	 * ReentrantLock to prevent telnet session is closed when adapter is retrieving statistics from the device.
	 */
	private final ReentrantLock reentrantLock = new ReentrantLock();

	/**
	 * Store previous/current ExtendedStatistics
	 */
	private ExtendedStatistics localExtendedStatistics;

	/**
	 * configManagement imported from the user interface
	 */
	private String configManagement;

	/**
	 * isConfigManagement to check if true accept all controllable properties, of false accept monitoring only
	 */
	private boolean isConfigManagement;

	/**
	 * isEmergencyDelivery to check if control flow is trigger
	 */
	private boolean isEmergencyDelivery;

	/**
	 * Retrieves {@link #configManagement}
	 *
	 * @return value of {@link #configManagement}
	 */
	public String getConfigManagement() {
		return configManagement;
	}

	/**
	 * Sets {@link #configManagement} value
	 *
	 * @param configManagement new value of {@link #configManagement}
	 */
	public void setConfigManagement(String configManagement) {
		this.configManagement = configManagement;
	}

	/**
	 * Constructor for AtenPDUCommunicator class
	 */
	public AtenPDUCommunicator() {
		this.setCommandErrorList(Collections.singletonList("Command incorrect"));
		this.setCommandSuccessList(Collections.singletonList("> "));
		this.setLoginSuccessList(Collections.singletonList("> "));
		this.setLoginErrorList(Collections.singletonList("Permission denied, please try again."));
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 *
	 * Check for available devices before retrieving the value
	 * ping latency information to Symphony
	 */
	@Override
	public int ping() throws Exception {
		if (isInitialized()) {
			long pingResultTotal = 0L;

			for (int i = 0; i < this.getPingAttempts(); i++) {
				long startTime = System.currentTimeMillis();

				try (Socket puSocketConnection = new Socket(this.host, this.getPort())) {
					puSocketConnection.setSoTimeout(this.getPingTimeout());
					if (puSocketConnection.isConnected()) {
						long pingResult = System.currentTimeMillis() - startTime;
						pingResultTotal += pingResult;
						if (this.logger.isTraceEnabled()) {
							this.logger.trace(String.format("PING OK: Attempt #%s to connect to %s on port %s succeeded in %s ms", i + 1, host, this.getPort(), pingResult));
						}
					} else {
						if (this.logger.isDebugEnabled()) {
							logger.debug(String.format("PING DISCONNECTED: Connection to %s did not succeed within the timeout period of %sms", host, this.getPingTimeout()));
						}
						return this.getPingTimeout();
					}
				} catch (SocketTimeoutException | ConnectException tex) {
					throw new SocketTimeoutException("Socket connection timed out");
				} catch (UnknownHostException tex) {
					throw new SocketTimeoutException("Socket connection timed out" + tex.getMessage());
				} catch (Exception e) {
					if (this.logger.isWarnEnabled()) {
						this.logger.warn(String.format("PING TIMEOUT: Connection to %s did not succeed, UNKNOWN ERROR %s: ", host, e.getMessage()));
					}
					return this.getPingTimeout();
				}
			}
			return Math.max(1, Math.toIntExact(pingResultTotal / this.getPingAttempts()));
		} else {
			throw new IllegalStateException("Cannot use device class without calling init() first");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		ExtendedStatistics extendedStatistics = new ExtendedStatistics();
		List<AdvancedControllableProperty> advancedControllableProperties = new ArrayList<>();
		Map<String, String> stats = new HashMap<>();
		Map<String, String> controlStats = new HashMap<>();
		reentrantLock.lock();

		try {
			this.timeout = controlSSHTimeout;
			if (!isEmergencyDelivery) {
				convertConfigManagement();
				retrieveMonitoring();
				if (failedMonitor.size() == AtenPDUConstant.NUMBER_OF_MONITORING_DATA) {
					StringBuilder sb = new StringBuilder();
					failedMonitor.forEach((failedMonitorGroupName, message) -> sb.append(message).append("\n"));
					throw new ResourceNotReachableException("Error while getting monitoring data, " + sb);
				}
				populateMonitoringAndControllingData(stats, controlStats, advancedControllableProperties);
				if (isConfigManagement) {
					stats.putAll(controlStats);
					extendedStatistics.setControllableProperties(advancedControllableProperties);
				}
				extendedStatistics.setStatistics(stats);
				localExtendedStatistics = extendedStatistics;
			}
			isEmergencyDelivery = false;
		} finally {
			this.timeout = statisticsSSHTimeout;
			reentrantLock.unlock();
		}

		return Collections.singletonList(localExtendedStatistics);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperties(List<ControllableProperty> list) throws Exception {
		if (CollectionUtils.isEmpty(list)) {
			throw new IllegalArgumentException("ControllableProperties can not be null or empty");
		}

		for (ControllableProperty p : list) {
			try {
				controlProperty(p);
			} catch (Exception e) {
				logger.error(String.format("Error when control property %s", p.getProperty()), e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {
		reentrantLock.lock();
		try {
			this.timeout = controlSSHTimeout;
			if (this.localExtendedStatistics == null || this.localExtendedStatistics.getStatistics() == null) {
				return;
			}
			isEmergencyDelivery = true;
			Map<String, String> stats = this.localExtendedStatistics.getStatistics();
			List<AdvancedControllableProperty> advancedControllableProperties = this.localExtendedStatistics.getControllableProperties();
			String value = String.valueOf(controllableProperty.getValue());
			String property = controllableProperty.getProperty();

			AtenPDUPropertiesEnum controlProperty = AtenPDUPropertiesEnum.getByName(property);
			switch (controlProperty) {
				case OUTLET_STATUS_1:
				case OUTLET_STATUS_2:
				case OUTLET_STATUS_3:
				case OUTLET_STATUS_4:
					String outletNumber = getOutletNumber(controlProperty);
					OutletStatusEnum outletStatus = OutletStatusEnum.getByValue(value.equalsIgnoreCase("1") ? AtenPDUConstant.ON : AtenPDUConstant.OFF);
					sendCommandToControlDevice(controlProperty.getName(), AtenPDUCommand.getSwitchControlCommand(outletNumber, outletStatus.getValue()));
					break;
				case REBOOT:
					sendCommandToControlDevice(controlProperty.getName(), AtenPDUCommand.REBOOT.getCommand());
					break;
				default:
					logger.debug("The property doesn't support " + property);
					break;
			}
			updateLocalControlValue(stats, advancedControllableProperties, property, value);
		} finally {
			this.timeout = statisticsSSHTimeout;
			reentrantLock.unlock();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalDestroy() {
		if (localExtendedStatistics != null && localExtendedStatistics.getStatistics() != null && localExtendedStatistics.getControllableProperties() != null) {
			localExtendedStatistics = null;
		}
		failedMonitor.clear();
		cacheKeyAndValue.clear();
		super.internalDestroy();
	}

	/**
	 * Retrieve monitoring data from the remote device
	 */
	private void retrieveMonitoring() throws Exception {
		for (AtenPDUCommand command : AtenPDUCommand.values()) {
			if (command.isMonitoring()) {
				String response = sendCommand(command.getCommand());
				switch (command) {
					case OUTLET_STATUS_1:
					case OUTLET_STATUS_2:
					case OUTLET_STATUS_3:
					case OUTLET_STATUS_4:
						cacheKeyAndValue.put(command.getName(), getDefaultValueOrNone(response.split("\r\n")[1].trim()));
						break;
					default:
						logger.debug(String.format("The adapter can't support monitoring properties name: %s", command.getName()));
						break;
				}
			}
		}
	}

	/**
	 * Populate monitoring and controlling data
	 *
	 * @param stats the stats are list of statistics
	 * @param advancedControllableProperties the list of AdvancedControllableProperty
	 */
	private void populateMonitoringAndControllingData(Map<String, String> stats, Map<String, String> controlStats, List<AdvancedControllableProperty> advancedControllableProperties) {
		for (AtenPDUPropertiesEnum property : AtenPDUPropertiesEnum.values()) {
			String key = property.getName();
			String data = AtenPDUConstant.NONE.equals(getDefaultValueOrNone(cacheKeyAndValue.get(key))) ? AtenPDUConstant.NONE : cacheKeyAndValue.get(key);
			switch (property) {
				case OUTLET_STATUS_1:
				case OUTLET_STATUS_2:
				case OUTLET_STATUS_3:
				case OUTLET_STATUS_4:
					if (AtenPDUConstant.NONE.equals(data)) {
						stats.put(key, AtenPDUConstant.NONE);
						continue;
					}
					if (!isConfigManagement) {
						stats.put(key,OutletStatusEnum.getByValue(data).getName());
						continue;
					}
					int initialValue = OutletStatusEnum.ON.getName().equals(OutletStatusEnum.getByValue(data).getName()) ? 1 : 0;
					controlStats.put(key, String.valueOf(initialValue));
					AdvancedControllableProperty outletControl = createSwitch(key, initialValue);
					advancedControllableProperties.add(outletControl);
					break;
				case REBOOT:
					controlStats.put(key, AtenPDUConstant.EMPTY);
					advancedControllableProperties.add(createButton(key, AtenPDUConstant.SYSTEM_REBOOT, AtenPDUConstant.EMPTY, 0L));
					break;
				default:
					logger.debug(String.format("The adapter can't support properties name: %s", property));
					break;
			}
		}
	}

	/**
	 * This method is used to send command to a remote device to get data
	 *
	 * @param command to be sent to the remote device
	 * @return response from the remote the device
	 */
	private String sendCommand(String command) throws Exception {
		try {
			String response = this.send(command.contains("\r") ? command : command.concat("\r"));
			if (StringUtils.isNullOrEmpty(response)) {
				throw new IllegalArgumentException("The response is empty or null");
			}
			return response;
		} catch (FailedLoginException e) {
			throw new FailedLoginException("Login failure, check credentials and try again.");
		} catch (Exception e) {
			failedMonitor.put(command, e.getMessage());
			logger.error("Error when execute command " + e.getMessage());
		}
		return AtenPDUConstant.NONE;
	}

	/**
	 * This method is used to send command to control device
	 *
	 * @param name of the property of device want to control
	 * @param command to send to device
	 */
	private void sendCommandToControlDevice(String name, String command) throws Exception {
		try {
			String response = this.send(command.contains("\r") ? command : command.concat("\r"));
			if (StringUtils.isNullOrEmpty(response)) {
				throw new IllegalArgumentException(String.format("Error when control %s, Syntax error command: %s", name, command));
			}
		}catch (FailedLoginException e) {
			throw new FailedLoginException("Login failure, check credential and try again.");
		} catch (Exception e) {
			throw new IllegalArgumentException(String.format("Error when control %s", name), e);
		}
	}

	/**
	 * Check null value
	 *
	 * @param value value need to check
	 * @return (none / value)
	 */
	private String getDefaultValueOrNone(String value) {
		return StringUtils.isNullOrEmpty(value) ? AtenPDUConstant.NONE : value;
	}

	/**
	 * Updates devices' control value, after the control command was executed with the specified value.
	 *
	 * @param stats the stats are list of Statistics
	 * @param advancedControllableProperties the advancedControllableProperty are AdvancedControllableProperty instance
	 * @param name of the control property
	 * @param value to set to the control property
	 */
	private void updateLocalControlValue(Map<String, String> stats, List<AdvancedControllableProperty> advancedControllableProperties, String name, String value) {
		stats.put(name, value);
		advancedControllableProperties.stream().filter(advancedControllableProperty ->
				name.equals(advancedControllableProperty.getName())).findFirst().ifPresent(advancedControllableProperty ->
				advancedControllableProperty.setValue(value));
	}

	/**
	 * Retrieve outlet number from controlProperty
	 *
	 * @param controlProperty outlet property want to control
	 * @return the outlet number want to get
	 */
	private String getOutletNumber(AtenPDUPropertiesEnum controlProperty) {
		switch (controlProperty) {
			case OUTLET_STATUS_1:
				return AtenPDUConstant.OUTLET1;
			case OUTLET_STATUS_2:
				return AtenPDUConstant.OUTLET2;
			case OUTLET_STATUS_3:
				return AtenPDUConstant.OUTLET3;
			case OUTLET_STATUS_4:
				return AtenPDUConstant.OUTLET4;
			default:
				return AtenPDUConstant.EMPTY;
		}
	}

	/**
	 * This method is used to validate input config management from user
	 */
	private void convertConfigManagement() {
		isConfigManagement = StringUtils.isNotNullOrEmpty(this.configManagement) && this.configManagement.equalsIgnoreCase(AtenPDUConstant.TRUE);
	}
}