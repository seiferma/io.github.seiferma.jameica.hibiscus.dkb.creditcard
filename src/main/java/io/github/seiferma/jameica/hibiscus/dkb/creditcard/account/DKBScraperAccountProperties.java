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
package io.github.seiferma.jameica.hibiscus.dkb.creditcard.account;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.logging.Logger;

public class DKBScraperAccountProperties {

	public static final String PROP_CC_NUMBER = "Card Number";
	public static final String PROP_WEB_USER = "Username";
	public static final String PROP_MAX_MONTH_IN_PAST = "Max Months in Past";
	public static final int PROP_MAX_MONTH_IN_PAST_DEFAULT = 6 * 12;

	public static String getCCNumber(Konto k) {
		return getMetaDataNumber(PROP_CC_NUMBER, k);
	}

	public static String getWebUser(Konto k) {
		return getMetaDataNumber(PROP_WEB_USER, k);
	}

	public static Integer getMaxMonthInPast(Konto k) {
		String propertyValue = getMetaDataNumber(PROP_MAX_MONTH_IN_PAST, k);
		if (StringUtils.isEmpty(propertyValue)) {
			return PROP_MAX_MONTH_IN_PAST_DEFAULT;
		}
		try {
			return Integer.parseInt(propertyValue);
		} catch (NumberFormatException e) {
			throw new IllegalStateException(String.format(
					"The maximum value for the months to go back for account \"%s\" is invalid.", k.toString()), e);
		}
	}

	public static List<String> getMetaDataNames() {
		return Arrays.asList(PROP_CC_NUMBER, PROP_WEB_USER, PROP_MAX_MONTH_IN_PAST);
	}

	private static String getMetaDataNumber(String metaData, Konto k) {
		try {
			return k.getMeta(metaData, null);
		} catch (RemoteException re) {
			Logger.error("Unable to determine meta data", re);
			return null;
		}
	}

}
