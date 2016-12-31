/*
Copyright (C) 2016 Stephan Seifermann

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
*/
package io.github.seiferma.jameica.hibiscus.dkb.creditcard.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.commons.lang3.Validate;

public class DateUtils {

	public enum Unit {
		DAY(Calendar.DAY_OF_MONTH),
		MONTH(Calendar.MONTH),
		YEAR(Calendar.YEAR);
		
		private final int calendarField;
		
		private Unit(int calendarField) {
			this.calendarField = calendarField;
		}
		
		public int getCalendarField() {
			return calendarField;
		}
	}
	
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");
	
	public static Date parseDate(String date) throws ParseException {
		return DATE_FORMAT.parse(date);
	}
	
	public static String formatDate(Date date) {
		return DATE_FORMAT.format(date);
	}
	
	public static Date getCurrentDate() {
		return new GregorianCalendar().getTime();
	}
	
	public static Date getDateAgo(Unit unit, int amount) {
		Validate.inclusiveBetween(0, Integer.MAX_VALUE, amount);
		
		Calendar calendar = new GregorianCalendar();
		calendar.add(unit.getCalendarField(), -amount);
		return calendar.getTime();
	}
}
