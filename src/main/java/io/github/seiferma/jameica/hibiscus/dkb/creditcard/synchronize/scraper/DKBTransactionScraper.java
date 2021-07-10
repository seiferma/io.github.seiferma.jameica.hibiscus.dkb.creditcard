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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.util.DateUtils;

public class DKBTransactionScraper {

	private static final String BASE_URL = "https://www.dkb.de";
	private static final String BALANCE_URL = BASE_URL + "/banking/finanzstatus/kreditkartenumsaetze?$event=init";
	private static final String CSV_EXPORT_URL = BASE_URL
			+ "/banking/finanzstatus/kreditkartenumsaetze?$event=csvExport";
	private static final String LOGOUT_URL = BASE_URL + "/DkbTransactionBanking/banner.xhtml?$event=logout";

	private final Date fromDate;
	private final boolean headless;

	public DKBTransactionScraper(Date fromDate) {
		this(fromDate, true);
	}
	
	public DKBTransactionScraper(Date fromDate, boolean headless) {
		this.fromDate = fromDate;
		this.headless = headless;
	}

	public String getBalancesCsv(String ccNumber, String webUser, String webPin) throws Exception {
		WebDriverManager.firefoxdriver().setup();
		FirefoxOptions firefoxOptions = new FirefoxOptions();
		firefoxOptions.setAcceptInsecureCerts(false);
		firefoxOptions.setHeadless(headless);
	    WebDriver driver = new FirefoxDriver(firefoxOptions);

		try {
			WebElement balancesForm = login(driver, webUser, webPin);
			chooseCorrectBalances(driver, balancesForm, ccNumber);
			return getBalancesCsv(driver);
		} finally {
			driver.get(LOGOUT_URL);
			driver.close();
		}
	}

	private static WebElement login(WebDriver webClient, String username, String password)
			throws MalformedURLException, IOException {

		// try to load balance page
		webClient.get(BALANCE_URL);

		// accept cookies
		try {
			WebElement acceptCookiesButton = webClient.findElement(By.id("popin_tc_privacy_button_2"));			
			acceptCookiesButton.click();
		} catch (NoSuchElementException e) {
			// could not find the cookie accept button, trying to continue
		}
		
		// submit login form
		WebElement usernameInput = webClient.findElement(By.id("loginInputSelector"));
		usernameInput.sendKeys(username);
		WebElement passwordInput = webClient.findElement(By.id("pinInputSelector"));
		passwordInput.sendKeys(password);
		passwordInput.submit();

		// wait for login to load second factor page
		WebDriverWait loginWait = new WebDriverWait(webClient, 30);
		loginWait.until(driver -> {
			driver.findElement(By.id("confirmForm"));
			return true;
		});

		// wait for balance page to load
		loginWait.until(driver -> {
			driver.findElement(By.className("evt-csvExport"));
			return true;
		});

		// find balances form
		return findBalancesForm(webClient);
	}

	private static WebElement findBalancesForm(WebDriver webClient) {
		List<WebElement> forms = webClient.findElements(By.className("form"));
		Optional<WebElement> balancesForm = forms.stream().filter(we -> Optional.ofNullable(we.getAttribute("action"))
				.map(actionUrl -> actionUrl.contains("kreditkartenumsaetze")).orElse(false)).findFirst();
		return balancesForm.orElseThrow(() -> new IllegalStateException("Could not find balances form."));
	}

	private void chooseCorrectBalances(WebDriver webClient, WebElement balancesForm, String ccNumber)
			throws IOException {
		// define wait
		WebDriverWait wait = new WebDriverWait(webClient, 30);

		// select matching account
		Select accountSelect = new Select(balancesForm.findElement(By.name("slAllAccounts")));
		WebElement foundOption = accountSelect.getOptions().stream()
				.filter(option -> matches(ccNumber, option.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("The account selection could not be found."));
		accountSelect.selectByValue(foundOption.getAttribute("value"));

		// select manual search period
		balancesForm = findBalancesForm(webClient);
		List<WebElement> filterTypes = balancesForm.findElements(By.name("filterType"));
		WebElement searchPeriodRadioButton = filterTypes.stream()
				.filter(we -> Optional.ofNullable(we.getAttribute("value")).map(value -> "DATE_RANGE".equals(value))
						.orElse(false))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("The search period radio button could not be found."));
		searchPeriodRadioButton.click();

		// select search period 6 months ago
		String currentDate = DateUtils.formatDate(new GregorianCalendar().getTime());
		String previousDate = DateUtils.formatDate(fromDate);

		WebElement fromDateText = balancesForm.findElement(By.name("postingDate"));
		fromDateText.sendKeys(previousDate);
		WebElement toDateText = balancesForm.findElement(By.name("toPostingDate"));
		toDateText.sendKeys(currentDate);

		// submit query
		toDateText.submit();

		// wait for submission to be finished
		wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
				.equals("complete"));
	}

	private static String getBalancesCsv(WebDriver webClient) throws MalformedURLException, IOException {
        return getUrlContent(webClient, CSV_EXPORT_URL);
	}
	
	private static String getUrlContent(WebDriver webClient, String url) {
		String script = String.format(
				"var callback = arguments[arguments.length - 1];\n" +
				"var xhr = new XMLHttpRequest();\n" + 
				"xhr.open('GET', '%s');\n" + 
				"xhr.send();\n" + 
				"xhr.onload = function() {\n" + 
				"  if (xhr.status != 200) {\n" + 
				"    callback(null);\n" + 
				"  } else {\n" + 
				"    callback(xhr.response);\n" + 
				"  }\n" + 
				"};"
				, url);
        Object response = ((JavascriptExecutor) webClient).executeAsyncScript(script);
        return (String)response;
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
