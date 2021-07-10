package io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.csvparser;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Date;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.scraper.DKBTransactionScraper;

public class DKBTransactionScraperManualTest {

	private DKBTransactionScraper subject;

	@BeforeEach
	public void setup() {
		long oneMonth = 30 * 24 * 60 * 60 * 1000;
		long now = new Date().getTime();
		Date oneMonthBeforeNow = new Date(now - oneMonth);
		subject = new DKBTransactionScraper(oneMonthBeforeNow, false);
	}

	@Test
	public void test() throws Exception {
		String ccNumber = System.getenv("ccNumber");
		String webUser = System.getenv("webUser");

		if (StringUtils.isBlank(ccNumber) || StringUtils.isBlank(webUser)) {
			return;
		}

		JPasswordField passwordField = new JPasswordField();
		int result = JOptionPane.showConfirmDialog(null, passwordField, "Enter PIN", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (result != JOptionPane.OK_OPTION) {
			fail("No PIN given.");
		}

		String webPassword = new String(passwordField.getPassword());
		String balances = subject.getBalancesCsv(ccNumber, webUser, webPassword);
		System.out.println(balances);
	}

}
