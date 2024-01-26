/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common;

/**
 * AtenPDUCommand class defined the enum contains all overall command of the device
 *
 * @author Kevin / Symphony Dev Team<br>
 * Created on 1/18/2024
 * @since 1.0.0
 */
public enum AtenPDUCommand {
	OUTLET_STATUS_1("Outlet1", "read status o01 simple", true),
	OUTLET_STATUS_2("Outlet2", "read status o02 simple", true),
	OUTLET_STATUS_3("Outlet3", "read status o03 simple", true),
	OUTLET_STATUS_4("Outlet4", "read status o04 simple", true),
	REBOOT("Reboot", "reboot", false);

	/**
	 * AtenPDUCommand constructor
	 *
	 * @param name of {@link #name}
	 * @param command of {@link #command}
	 * @param isMonitoring of {@link #command}
	 */
	AtenPDUCommand(String name, String command, boolean isMonitoring) {
		this.name = name;
		this.command = command;
		this.isMonitoring = isMonitoring;
	}

	private String name;
	private String command;
	private boolean isMonitoring;

	/**
	 * Retrieves {@link #name}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Retrieves {@link #command}
	 *
	 * @return value of {@link #command}
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Retrieves {@link #isMonitoring}
	 *
	 * @return value of {@link #isMonitoring}
	 */
	public boolean isMonitoring() {
		return isMonitoring;
	}

	/**
	 * Obtain a command to control a specific outlet's status
	 *
	 * @param outlet of device [01 ~ 04]
	 * @param status of outlet [on/off]
	 */
	public static String getSwitchControlCommand(String outlet, String status) {
		return "sw o" + outlet + " imme " + status;
	}
}