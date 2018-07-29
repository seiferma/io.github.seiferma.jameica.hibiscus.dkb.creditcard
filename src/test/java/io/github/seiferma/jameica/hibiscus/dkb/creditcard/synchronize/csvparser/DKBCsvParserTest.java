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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.DKBTransaction;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.csvparser.DKBCsvParser;

public class DKBCsvParserTest {

	private static final String TEST_RESOURCE_BASE_PATH = "io/github/seiferma/jameica/hibiscus/dkb/creditcard/synchronize/csvparser/";
	private DKBCsvParser subject;

	@Before
	public void setup() throws IOException {
		String csvString = readFile("regularFile.csv");
		subject = new DKBCsvParser(csvString);
	}

	@Test
	public void testGetSaldo() throws IOException {
		assertEquals(123456, subject.getBalanceInCents());
	}

	@Test
	public void testGetSaldoDate() throws IOException {
		assertEquals(new GregorianCalendar(2016, 11, 27).getTime(), subject.getBalanceDate());
	}

	@Test
	public void testTransactions() throws IOException {
		Iterable<DKBTransaction> actual = subject.getTransactions();

		List<DKBTransaction> expected = new ArrayList<>();
		expected.add(DKBTransactionImpl.create().setIsBooked(false).setValueDate(createDate(2016, 11, 28))
				.setBookingDate(createDate(2016, 11, 27)).setDescription("Some Retail Store DESmallVillage")
				.setAmountInCents(-12345).build());
		expected.add(DKBTransactionImpl.create().setIsBooked(true).setValueDate(createDate(2016, 11, 27))
				.setBookingDate(createDate(2016, 11, 24)).setDescription("An Online Merchant XYZ12")
				.setAmountInCents(-1234).build());
		expected.add(DKBTransactionImpl.create().setIsBooked(true).setValueDate(createDate(2016, 11, 23))
				.setBookingDate(createDate(2016, 11, 22)).setDescription("Added money to acc").setAmountInCents(12345)
				.build());

		assertThat(actual, contains(expected.toArray()));
	}

	@Test
	public void testEmptyTransactions() throws IOException {
		String csvString = readFile("emptyTransactions.csv");
		subject = new DKBCsvParser(csvString);
		Iterable<DKBTransaction> actualTransactions = subject.getTransactions();

		assertThat(actualTransactions, is(emptyIterable()));
	}
	
	@Test
	public void testLeadingZero() throws IOException {
		String csvString = readFile("leadingZero.csv");
		subject = new DKBCsvParser(csvString);
		Iterable<DKBTransaction> actual = subject.getTransactions();

		List<DKBTransaction> expected = new ArrayList<>();
		expected.add(DKBTransactionImpl.create().setIsBooked(false).setValueDate(createDate(2016, 11, 28))
				.setBookingDate(createDate(2016, 11, 27)).setDescription("Some Retail Store DESmallVillage")
				.setAmountInCents(-45).build());
		expected.add(DKBTransactionImpl.create().setIsBooked(true).setValueDate(createDate(2016, 11, 27))
				.setBookingDate(createDate(2016, 11, 24)).setDescription("An Online Merchant XYZ12")
				.setAmountInCents(-4).build());
		expected.add(DKBTransactionImpl.create().setIsBooked(true).setValueDate(createDate(2016, 11, 23))
				.setBookingDate(createDate(2016, 11, 22)).setDescription("Added money to acc").setAmountInCents(5)
				.build());

		assertThat(actual, contains(expected.toArray()));
	}
	
	@Test
	public void testNegativeBalance() throws IOException {
		String csvString = readFile("negativeBalance.csv");
		subject = new DKBCsvParser(csvString);
		assertThat(subject.getBalanceInCents(), is(-6));
	}
	
	@Test
	public void testNoCentsSaldo() throws IOException {
		String csvString = readFile("noCentsSaldo.csv");
		subject = new DKBCsvParser(csvString);
		assertThat(subject.getBalanceInCents(), is(123400));
	}

	private static Date createDate(int year, int month, int day) {
		return new GregorianCalendar(year, month, day).getTime();
	}

	private static String readFile(String filename) throws IOException {
		try (InputStream fileStream = DKBCsvParserTest.class.getClassLoader()
				.getResourceAsStream(TEST_RESOURCE_BASE_PATH + filename)) {
			return IOUtils.toString(fileStream, "ISO-8859-15");
		}
	}
}
