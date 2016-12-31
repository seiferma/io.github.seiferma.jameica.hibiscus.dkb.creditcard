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
package io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.csvparser;

import java.util.Date;

import io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.DKBTransaction;

public class DKBTransactionImpl implements DKBTransaction {

	public static class DKBTransactionBuilder {
		
		private final DKBTransactionImpl impl = new DKBTransactionImpl();
		
		private DKBTransactionBuilder() {
			
		}
		
		public DKBTransactionBuilder setValueDate(Date date) {
			impl.valueDate = date;
			return this;
		}
		
		public DKBTransactionBuilder setBookingDate(Date date) {
			impl.bookingDate = date;
			return this;
		}
		
		public DKBTransactionBuilder setDescription(String description) {
			impl.description = description;
			return this;
		}
		
		public DKBTransactionBuilder setAmountInCents(int amount) {
			impl.amountInCents = amount;
			return this;
		}
		
		public DKBTransactionBuilder setIsBooked(boolean isBooked) {
			impl.isBooked = isBooked;
			return this;
		}
		
		public DKBTransaction build() {
			return impl;
		}
	}
	
	private Date valueDate;
	private Date bookingDate;
	private String description;
	private int amountInCents;
	private boolean isBooked;
	
	public static DKBTransactionBuilder create() {
		return new DKBTransactionBuilder();
	}
	
	private DKBTransactionImpl() {
	}
	
	@Override
	public Date getValueDate() {
		return valueDate;
	}

	@Override
	public Date getBookingDate() {
		return bookingDate;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public int getAmountInCents() {
		return amountInCents;
	}

	@Override
	public boolean isBooked() {
		return isBooked;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + amountInCents;
		result = prime * result + ((bookingDate == null) ? 0 : bookingDate.hashCode());
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + (isBooked ? 1231 : 1237);
		result = prime * result + ((valueDate == null) ? 0 : valueDate.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DKBTransactionImpl other = (DKBTransactionImpl) obj;
		if (amountInCents != other.amountInCents)
			return false;
		if (bookingDate == null) {
			if (other.bookingDate != null)
				return false;
		} else if (!bookingDate.equals(other.bookingDate))
			return false;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (isBooked != other.isBooked)
			return false;
		if (valueDate == null) {
			if (other.valueDate != null)
				return false;
		} else if (!valueDate.equals(other.valueDate))
			return false;
		return true;
	}
	
	
	
}
