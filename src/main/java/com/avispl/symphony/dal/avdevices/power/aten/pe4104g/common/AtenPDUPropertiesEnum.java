/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common;

import java.util.Arrays;

/**
 * This enum represents a list of properties and metrics related to a Aten system.
 *
 * @author Kevin / Symphony Dev Team<br>
 * Created on 1/18/2024
 * @since 1.0.0
 */
public enum AtenPDUPropertiesEnum {
	OUTLET_STATUS_1("Outlet1"),
	OUTLET_STATUS_2("Outlet2"),
	OUTLET_STATUS_3("Outlet3"),
	OUTLET_STATUS_4("Outlet4"),
	REBOOT("Reboot");

	private final String name;

	/**
	 * Create a new AtenPDUPropertiesList with the specified  name
	 *
	 * @param name of the
	 */
	AtenPDUPropertiesEnum(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@link #name}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * This method is used to get properties metric by name
	 *
	 * @param name is the name of device metric
	 * @return AtenPDUPropertiesList is the device metric want to get
	 */
	public static AtenPDUPropertiesEnum getByName(String name) {
		return Arrays.stream(AtenPDUPropertiesEnum.values()).filter(property -> property.getName().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(String.format("Device metric %s is not supported.", name)));
	}

}