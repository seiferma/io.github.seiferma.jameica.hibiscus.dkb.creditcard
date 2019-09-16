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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.DKBTransaction;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.util.DateUtils;

public class DKBCsvParser {

	private final CSVFormat csvFormat;
	private final String lineSeparator;
	private final String metaDataCsv;
	private final String contentCsv;

	public DKBCsvParser(String csvContent) throws IOException {
		this.lineSeparator = determineLineSeparator(csvContent);
		this.csvFormat = createCsvFormat(lineSeparator);
		this.metaDataCsv = getMetaDataString(csvContent, lineSeparator);
		this.contentCsv = getContentString(csvContent, lineSeparator);
	}

	public int getBalanceInCents() throws IOException {
		CSVParser parser = CSVParser.parse(metaDataCsv, csvFormat);
		List<CSVRecord> records = parser.getRecords();
		Optional<String> foundSaldo = records.stream().filter(r -> "Saldo:".equals(r.get(0))).map(r -> r.get(1))
				.findFirst();
		if (!foundSaldo.isPresent()) {
			throw new IOException("Could not find balance record.");
		}
		Pattern saldoPattern = Pattern.compile("(-?[0-9]+)([.]([0-9]{1,2}))? EUR");
		Matcher saldoMatcher = saldoPattern.matcher(foundSaldo.get());
		if (!saldoMatcher.matches()) {
			throw new IOException("Could not parse saldo.");
		}
		
		String euroString = saldoMatcher.group(1);
		String centString = saldoMatcher.group(3) == null ? "0" : saldoMatcher.group(3);
		
		return getCentsFromString(euroString, centString);
	}

	public Date getBalanceDate() throws IOException {
		CSVParser parser = CSVParser.parse(metaDataCsv, csvFormat);
		Optional<String> foundDate = parser.getRecords().stream().filter(r -> "Datum:".equals(r.get(0)))
				.map(r -> r.get(1)).findFirst();
		if (!foundDate.isPresent()) {
			throw new IOException("Could not find balance date.");
		}
		try {
			return DateUtils.parseDate(foundDate.get());
		} catch (ParseException e) {
			throw new IOException("Could not parse balance date.", e);
		}
	}

	public Iterable<DKBTransaction> getTransactions() throws IOException {
		CSVParser parser = CSVParser.parse(contentCsv, csvFormat.withFirstRecordAsHeader());
		List<DKBTransaction> transactions = new ArrayList<>();
		for (CSVRecord csvRecord : parser.getRecords()) {
			transactions.add(createTransaction(csvRecord));
		}
		return transactions;
	}

	private static DKBTransaction createTransaction(CSVRecord record) throws IOException {
		String bookedString = record.get(0);
		boolean isBooked = "Ja".equals(bookedString);

		String valueDateString = record.get(1);
		Date valueDate;
		try {
			valueDate = DateUtils.parseDate(valueDateString);
		} catch (ParseException e) {
			throw new IOException("Could not parse value date.", e);
		}

		String bookingDateString = record.get(2);
		Date bookingDate;
		try {
			bookingDate = DateUtils.parseDate(bookingDateString);
		} catch (ParseException e) {
			throw new IOException("Could not parse booking date.", e);
		}

		String description = record.get(3);
		description = description.replaceAll("    ", "");

		String amountString = record.get(4);
		amountString = amountString.replace(".", "");
		Pattern amountPattern = Pattern.compile("(-?[0-9]+),([0-9]{1,2})");
		Matcher amountMatcher = amountPattern.matcher(amountString);
		if (!amountMatcher.matches() || amountMatcher.groupCount() != 2) {
			throw new IOException(String.format("Could not parse transaction amount (%s).", amountString));
		}
		
		int amountInCents = getCentsFromString(amountMatcher.group(1), amountMatcher.group(2));

		return DKBTransactionImpl.create().setIsBooked(isBooked).setValueDate(valueDate).setBookingDate(bookingDate)
				.setDescription(description).setAmountInCents(amountInCents).build();

	}

	private static String getContentString(String csvContent, String lineSeparator) throws IOException {
		int metaDataLength = getMetaDataHeaderLength(csvContent, lineSeparator);
		return csvContent.substring(metaDataLength);
	}

	private static String getMetaDataString(String csvContent, String lineSeparator) throws IOException {
		int metaDataLength = getMetaDataHeaderLength(csvContent, lineSeparator);
		return csvContent.substring(0, metaDataLength);
	}

	private static int getMetaDataHeaderLength(String csvContent, String lineSeparator) throws IOException {
		int length = 0;

		try (BufferedReader br = new BufferedReader(new StringReader(csvContent))) {
			for (String line = br.readLine(); line != null; line = br.readLine()) {
				int numberOfDelimiters = StringUtils.countMatches(line, ';');
				if (numberOfDelimiters != 0 && numberOfDelimiters != 2) {
					return length;
				}
				length += line.length() + lineSeparator.length();
			}
		}

		return length;
	}

	private String determineLineSeparator(String csvContent) {
		int lineFeedIndex = csvContent.indexOf('\n');
		if (lineFeedIndex > 0 && csvContent.charAt(lineFeedIndex - 1) == '\r') {
			return "\r\n";
		}
		return "\n";
	}

	private static CSVFormat createCsvFormat(String lineSeparator) {
		return CSVFormat.DEFAULT.withDelimiter(';').withRecordSeparator(lineSeparator);
	}
	
	private static int getCentsFromString(String euroString, String centString) throws IOException {
		boolean negative = euroString.startsWith("-");
		try {
			int euros = Math.abs(Integer.parseInt(euroString) * 100);
			int cents = Integer.parseInt(centString);
			return (negative ? -1 : 1) * (euros + cents);
		} catch (NumberFormatException e) {
			throw new IOException("Could not parse balance.", e);
		}
	}

}
