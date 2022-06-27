package com.esri.geoevent.adapter.flextext;

import java.util.Date;

import com.esri.ges.util.DateUtil;

public class DateUtilWrapper extends DateUtil {

	/**
	 * Added for backward compatibility with 10.4.0. This method was added at 10.7.0
	 */
	public static Date convert(long milli) {
		return Date.from(java.time.Instant.ofEpochMilli(milli));
	}

}
