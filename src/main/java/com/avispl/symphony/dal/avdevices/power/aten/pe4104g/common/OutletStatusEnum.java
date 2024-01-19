/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common;

import java.util.Arrays;

/**
 * This enum represents the status of outlet
 *
 * @author Kevin / Symphony Dev Team<br>
 * Created on 1/18/2024
 * @since 1.0.0
 */
public enum OutletStatusEnum {
	ON("On", "on"),
	OFF("Off", "off");

	private final String name;
	private final String value;

	/**
	 * Create a new OutletStatusEnum with the specified name and value
	 *
	 * @param name of outlet status
	 * @param value the corresponding value of the outlet status
	 */
	OutletStatusEnum(String name, String value) {
		this.name = name;
		this.value = value;
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
	 * Retrieves {@link #value}
	 *
	 * @return value of {@link #value}
	 */
	public String getValue() {
		return value;
	}


	/**
	 * This method is used to get OutletStatus by value
	 *
	 * @param value is the status of outlet
	 * @return OutletStatus want to get
	 */
	public static OutletStatusEnum getByValue(String value) {
		return Arrays.stream(OutletStatusEnum.values()).filter(status -> status.getValue().equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(String.format("Outlet status with value %s is not supported.", value)));
	}
}