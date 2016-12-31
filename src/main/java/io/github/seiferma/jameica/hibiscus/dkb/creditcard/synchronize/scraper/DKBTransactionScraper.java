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
package io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize.scraper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;

import io.github.seiferma.jameica.hibiscus.dkb.creditcard.util.DateUtils;

public class DKBTransactionScraper {

	private static final String BASE_URL = "https://www.dkb.de";
	private static final String BALANCE_URL = BASE_URL + "/banking/finanzstatus/kreditkartenumsaetze?$event=init";
	private static final String CSV_EXPORT_URL = BASE_URL + "/banking/finanzstatus/kreditkartenumsaetze?$event=csvExport";
	private static final String LOGOUT_URL = BASE_URL + "/DkbTransactionBanking/banner.xhtml?$event=logout";
	
	private final Date fromDate;
	
	public DKBTransactionScraper(Date fromDate) {
		this.fromDate = fromDate;
	}
	
	public String getBalancesCsv(String ccNumber, String webUser, String webPin) throws Exception {		
		try (final WebClient webClient = new WebClient()) {
			try {
				webClient.getOptions().setCssEnabled(false);
				webClient.getOptions().setJavaScriptEnabled(false);
				HtmlPage balancesSelectionPage = login(webClient, webUser, webPin);
				chooseCorrectBalances(webClient, balancesSelectionPage, ccNumber); 
				return getBalancesCsv(webClient);
			} finally {
				webClient.getPage(LOGOUT_URL);
			}
		} catch (Exception e) {
			throw e;
		}
	}
	
	private static HtmlPage login(WebClient webClient, String username, String password) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		HtmlPage page = webClient.getPage(BALANCE_URL);
		HtmlForm loginForm = page.getFormByName("login");
		HtmlInput loginUsernameInput = loginForm.getInputByName("j_username");
		loginUsernameInput.setValueAttribute(username);
		HtmlInput loginPasswordInput = loginForm.getInputByName("j_password");
		loginPasswordInput.setValueAttribute(password);
		return submitForm(loginForm);
	}
		
	private HtmlPage chooseCorrectBalances(WebClient webClient, HtmlPage balancesSelectionPage, String ccNumber) throws IOException {	
		Optional<HtmlForm> balancesForm = balancesSelectionPage.getForms().stream().filter(f -> "/banking/finanzstatus/kreditkartenumsaetze".equals(f.getActionAttribute())).findFirst();
		if (!balancesForm.isPresent()) {
			throw new IllegalStateException("The balances selection could not be found.");
		}
		
		// select matching account
		HtmlSelect accountSelect = balancesForm.get().getSelectByName("slAllAccounts");
		Optional<HtmlOption> foundOption = accountSelect.getOptions().stream().filter(o -> matches(ccNumber, o.getText())).findFirst();
		if (!foundOption.isPresent()) {
			throw new IllegalStateException("The account selection could not be found.");
		}
		foundOption.get().setSelected(true);
		
		// select manual search period
		Optional<HtmlRadioButtonInput> searchPeriodRadioButton = balancesForm.get().getRadioButtonsByName("searchPeriod").stream().filter(r -> "0".equals(r.getValueAttribute())).findFirst();
		if (!searchPeriodRadioButton.isPresent()) {
			throw new IllegalStateException("The search period radio button could not be found.");
		}
		searchPeriodRadioButton.get().setChecked(true);
		
		// select search period 6 months ago		
		String currentDate = DateUtils.formatDate(new GregorianCalendar().getTime());
		String previousDate = DateUtils.formatDate(fromDate);
		HtmlInput fromDateText = balancesForm.get().getInputByName("postingDate");
		fromDateText.setValueAttribute(previousDate);
		HtmlInput toDateText = balancesForm.get().getInputByName("toPostingDate");
		toDateText.setValueAttribute(currentDate);
		
		// submit query
		return submitForm(balancesForm.get());
	}

	private static String getBalancesCsv(WebClient webClient) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		TextPage txtPage = webClient.getPage(CSV_EXPORT_URL);
		return txtPage.getContent();
	}
	
	private static <P extends Page> P submitForm(HtmlForm form) throws IOException {
		HtmlElement button = (HtmlButton)form.getPage().createElement(HtmlButton.TAG_NAME);
		button.setAttribute("type", "submit");
		form.appendChild(button);
		return button.click();
	}
	
	private static boolean matches(String ccNumber, String anonymizedCcNumber) {
		String candidate = StringUtils.trim(anonymizedCcNumber);
		Pattern p = Pattern.compile(".*?([0-9]{4})[0-9*]*([0-9]{4}).*");
		Matcher m = p.matcher(candidate);
		if (!m.matches() || m.groupCount() < 2) {
			return false;
		}
		String foundFirstDigits = m.group(1);
		String foundLastDigits = m.group(2);
		
		String requiredFirstDigits = ccNumber.substring(0, 4);
		String requiredLastDigits = ccNumber.substring(ccNumber.length() - 4, ccNumber.length());
		
		return requiredFirstDigits.equals(foundFirstDigits) && requiredLastDigits.equals(foundLastDigits);
	}
	
}
