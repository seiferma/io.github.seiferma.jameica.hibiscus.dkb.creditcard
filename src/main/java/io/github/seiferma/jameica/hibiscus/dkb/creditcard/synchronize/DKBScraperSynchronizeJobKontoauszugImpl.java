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
package io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.text.WordUtils;

import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.gui.dialogs.PINDialog;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.messaging.ObjectChangedMessage;
import de.willuhn.jameica.hbci.messaging.ObjectDeletedMessage;
import de.willuhn.jameica.hbci.messaging.ObjectMessage;
import de.willuhn.jameica.hbci.messaging.SaldoMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJobKontoauszug;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;
import de.willuhn.util.ProgressMonitor;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.DKBScraperPlugin;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.account.DKBScraperAccountProperties;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.csvparser.DKBCsvParser;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.scraper.DKBTransactionScraper;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.util.DateUtils;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.util.DateUtils.Unit;

public class DKBScraperSynchronizeJobKontoauszugImpl extends SynchronizeJobKontoauszug
		implements DKBScraperSynchronizeJobKontoauszug {

	private final static I18N i18n = Application.getPluginLoader().getPlugin(DKBScraperPlugin.class).getResources().getI18N();

	@Override
	public void execute(ProgressMonitor monitor) throws Exception {
		Konto k = getKonto();

		String ccNumber = DKBScraperAccountProperties.getCCNumber(k);
		String webUser = DKBScraperAccountProperties.getWebUser(k);
		int maxMonthsToGoBack = DKBScraperAccountProperties.getMaxMonthInPast(k);
		String webPin = getPin();

		Date fetchFrom = determineFetchDate(k, maxMonthsToGoBack);
		monitor.log(i18n.tr("Collecting transactions back to {0}.", DateUtils.formatDate(fetchFrom)));
		DKBTransactionScraper transactionScraper = new DKBTransactionScraper(fetchFrom);
		String csvText = transactionScraper.getBalancesCsv(ccNumber, webUser, webPin);
		monitor.log(i18n.tr("Parsing received answer."));
		DKBCsvParser parser = new DKBCsvParser(csvText);

		monitor.log(i18n.tr("Creating transactions from received answer."));
		int balance = parser.getBalanceInCents();
		List<Umsatz> transactions = new ArrayList<>();
		for (DKBTransaction parsedTransaction : parser.getTransactions()) {
			transactions.add(createTransaction(parsedTransaction, k));
		}

		monitor.log(i18n.tr("Integrating transactions into database."));
		k.transactionBegin();
		try {
			integrateTransactions(k, transactions);
			setSaldo(k, balance);
			k.transactionCommit();
		} catch (Exception e) {
			k.transactionRollback();
			throw e;
		}
	}

	private void setSaldo(Konto k, int balance) throws RemoteException, ApplicationException {
		k.setSaldo(balance / 100.0);
		k.store();
		sendMessage(new SaldoMessage(k));
	}

	private void integrateTransactions(Konto k, Collection<Umsatz> transactions)
			throws RemoteException, ApplicationException {
		Date fetchFromDate = getLatestDate(transactions);
		Date fetchUntilDate = DateUtils.getCurrentDate();
		Set<Umsatz> newTransactions = new LinkedHashSet<>(transactions);

		// patch existing transactions
		for (@SuppressWarnings("unchecked")
		DBIterator<Umsatz> storedTransactions = k.getUmsaetze(fetchFromDate, fetchUntilDate); storedTransactions
				.hasNext();) {
			Umsatz storedTransaction = storedTransactions.next();
			Optional<Umsatz> foundNewTransaction = findTransaction(storedTransaction, newTransactions);

			if (storedTransaction.hasFlag(Umsatz.FLAG_NOTBOOKED)) {
				if (!foundNewTransaction.isPresent()) {
					storedTransaction.delete();
					sendMessage(new ObjectDeletedMessage(storedTransaction));
				} else if (!foundNewTransaction.get().hasFlag(Umsatz.FLAG_NOTBOOKED)) {
					storedTransaction.setFlags(storedTransaction.getFlags() & ~Umsatz.FLAG_NOTBOOKED);
					storedTransaction.store();
					sendMessage(new ObjectChangedMessage(storedTransaction));
				}
			}

			if (foundNewTransaction.isPresent()) {
				newTransactions.remove(foundNewTransaction.get());
			}
		}

		// add new transactions
		for (Umsatz newTransaction : newTransactions) {
			newTransaction.store();
			sendMessage(new ImportMessage(newTransaction));
		}

	}

	private Umsatz createTransaction(DKBTransaction transaction, Konto k) throws RemoteException {
		Umsatz hbciTransaction = Settings.getDBService().createObject(Umsatz.class, null);
		hbciTransaction.setKonto(k);
		hbciTransaction.setDatum(transaction.getBookingDate());
		hbciTransaction.setValuta(transaction.getValueDate());
		hbciTransaction.setBetrag(transaction.getAmountInCents() / 100.0);
		hbciTransaction
				.setWeitereVerwendungszwecke(WordUtils.wrap(transaction.getDescription(), 35, "\n", true).split("\n"));
		if (!transaction.isBooked()) {
			int flags = hbciTransaction.getFlags() | Umsatz.FLAG_NOTBOOKED;
			hbciTransaction.setFlags(flags);
		}
		return hbciTransaction;
	}

	private Date determineFetchDate(Konto k, int maxMonthsToGoBack) throws RemoteException {
		Date earliestFetchDate = DateUtils.getDateAgo(Unit.MONTH, maxMonthsToGoBack);
		Date fetchFrom = null;

		@SuppressWarnings("unchecked")
		DBIterator<Umsatz> storedTransactions = k.getUmsaetze(earliestFetchDate, DateUtils.getCurrentDate());
		while (storedTransactions.hasNext()) {
			Umsatz currentTransaction = storedTransactions.next();
			if (fetchFrom == null || currentTransaction.hasFlag(Umsatz.FLAG_NOTBOOKED)) {
				fetchFrom = currentTransaction.getDatum();
			}
		}

		if (fetchFrom == null) {
			fetchFrom = earliestFetchDate;
		}

		return fetchFrom;
	}

	private static Optional<Umsatz> findTransaction(Umsatz needle, Iterable<Umsatz> haystack) throws RemoteException {
		for (Umsatz transaction : haystack) {
			if (matches(needle, transaction)) {
				return Optional.of(transaction);
			}
		}
		return Optional.empty();
	}

	private static boolean matches(Umsatz t1, Umsatz t2) throws RemoteException {
		if (!t1.getDatum().equals(t2.getDatum())) {
			return false;
		}

		if (Math.abs(t1.getBetrag() - t2.getBetrag()) >= 0.01) {
			return false;
		}

		if (!Arrays.equals(t1.getWeitereVerwendungszwecke(), t2.getWeitereVerwendungszwecke())) {
			return false;
		}

		return true;
	}

	private static Date getLatestDate(Iterable<Umsatz> transactions) throws RemoteException {
		Date latestDate = null;
		for (Umsatz transaction : transactions) {
			Date transactionDate = transaction.getDatum();
			if (latestDate == null || latestDate.after(transactionDate)) {
				latestDate = transactionDate;
			}
		}
		return latestDate;
	}

	private static void sendMessage(ObjectMessage msg) {
		Application.getMessagingFactory().sendMessage(msg);
	}

	private static String getPin() throws Exception {
		PINDialog dialog = new PINDialog("");
		String pin = (String) dialog.open();
		return pin;
	}

}
