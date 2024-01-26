/*
 *
 *  * Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 *
 */

package com.avispl.symphony.dal.avdevices.power.aten.pe4104g;


import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.dal.avdevices.power.aten.pe4104g.common.AtenPDUPropertiesEnum;

/**
 * AtenPDUCommunicatorTest for unit test of AtenPDUCommunicator
 *
 * @author Kevin / Symphony Dev Team<br>
 * Created on 1/15/2024
 * @since 1.0.0
 */
@Tag("RealDevice")
class AtenPDUCommunicatorTest {
	private AtenPDUCommunicator atenPDUCommunicator;

	@BeforeEach
	public void setup() throws Exception {
		atenPDUCommunicator = new AtenPDUCommunicator();
		atenPDUCommunicator.setHost("10.10.2.14");
		atenPDUCommunicator.setPort(22);
		atenPDUCommunicator.setLogin("");
		atenPDUCommunicator.setPassword("");
		atenPDUCommunicator.init();
		atenPDUCommunicator.connect();
		atenPDUCommunicator.setConfigManagement("true");
	}

	@AfterEach
	public void destroy() throws Exception {
		atenPDUCommunicator.disconnect();
	}

	/**
	 * Unit test to verify the functionality of the "getMultipleStatistics" method in the AtenPDUCommunicator class.
	 * This test ensures that the method correctly retrieves multiple statistics and verifies the expected size of the result.
	 *
	 * @throws Exception if an error occurs during the test execution.
	 */
	@Test
	void testGetMultipleStatistics() throws Exception {
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) atenPDUCommunicator.getMultipleStatistics().get(0);
		List<AdvancedControllableProperty> advancedControllableProperties = extendedStatistics.getControllableProperties();
		Map<String, String> statistics = extendedStatistics.getStatistics();
		Assertions.assertEquals(5, statistics.size());
		Assertions.assertEquals(5, advancedControllableProperties.size());
	}

	/**
	 * Test default config management
	 *
	 * Expect default config management successfully
	 * @throws Exception if an error occurs during the test execution.
	 */
	@Test
	@Order(2)
	void testDefaultConfigManagement() throws Exception {
		atenPDUCommunicator.setConfigManagement("false");
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) atenPDUCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistics.getStatistics();
		Assertions.assertEquals(4, statistics.size());
	}

	/**
	 * Switch off the outlet to test the control
	 *
	 * Expect outlet turn off successfully
	 * @throws Exception if an error occurs during the test execution.
	 */
	@Test
	void testControlSwitchOffOutlet() throws Exception {
		atenPDUCommunicator.setConfigManagement("true");
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) atenPDUCommunicator.getMultipleStatistics().get(0);
		List<AdvancedControllableProperty> controllablePropertyList = extendedStatistics.getControllableProperties();
		String outletStatusOn = controllablePropertyList.stream().filter(item -> item.getName().equals(AtenPDUPropertiesEnum.OUTLET_STATUS_1.getName())).findFirst().get().getValue().toString();
		Assertions.assertEquals("1", outletStatusOn);

		ControllableProperty controllableProperty = new ControllableProperty();
		String key = "Outlet1";
		String value = "0";
		controllableProperty.setValue(value);
		controllableProperty.setProperty(key);
		atenPDUCommunicator.controlProperty(controllableProperty);
		extendedStatistics = (ExtendedStatistics) atenPDUCommunicator.getMultipleStatistics().get(0);
		controllablePropertyList  = extendedStatistics.getControllableProperties();
		String currentValue = String.valueOf(controllablePropertyList .stream().filter(item -> item.getName().equals(key)).findFirst().get().getValue());
		Assertions.assertEquals("0", currentValue);
	}

	/**
	 * Switch on the outlet to test the control
	 *
	 * Expect outlet turn on successfully
	 * @throws Exception if an error occurs during the test execution.
	 */
	@Test
	void testControlSwitchOnOutlet() throws Exception {
		atenPDUCommunicator.setConfigManagement("true");
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) atenPDUCommunicator.getMultipleStatistics().get(0);
		List<AdvancedControllableProperty> controllablePropertyList = extendedStatistics.getControllableProperties();
		String outletStatusOff = controllablePropertyList.stream().filter(item -> item.getName().equals(AtenPDUPropertiesEnum.OUTLET_STATUS_1.getName())).findFirst().get().getValue().toString();
		Assertions.assertEquals("0", outletStatusOff);

		ControllableProperty controllableProperty = new ControllableProperty();
		String key = "Outlet1";
		String value = "1";
		controllableProperty.setValue(value);
		controllableProperty.setProperty(key);
		atenPDUCommunicator.controlProperty(controllableProperty);
		atenPDUCommunicator.getMultipleStatistics();
		extendedStatistics = (ExtendedStatistics) atenPDUCommunicator.getMultipleStatistics().get(0);
		controllablePropertyList = extendedStatistics.getControllableProperties();
		String currentValue = String.valueOf(controllablePropertyList.stream().filter(item -> item.getName().equals(key)).findFirst().get().getValue());
		Assertions.assertEquals("1", currentValue);
	}

	/**
	 * Test Reboot Control
	 *
	 * Expect device reboot successfully
	 * @throws Exception if an error occurs during the test execution.
	 */
	@Test
	void testReboot() throws Exception {
		atenPDUCommunicator.setConfigManagement("true");
		atenPDUCommunicator.getMultipleStatistics();
		atenPDUCommunicator.getMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String key = "Reboot";
		String value = "";
		controllableProperty.setValue(value);
		controllableProperty.setProperty(key);
		atenPDUCommunicator.controlProperty(controllableProperty);
		Assertions.assertThrows(SocketTimeoutException.class, () -> atenPDUCommunicator.getMultipleStatistics(), "Expect timeout exception, due to the device already rebooting");
	}
}